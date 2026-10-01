/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * VersionService, fetches versions straight from maven-metadata.xml of a repository
 * (Maven Central and JitPack serve it directly, so no database nor scraping is needed) with a TTL cache.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class VersionService {

    private record Entry(long time, List<String> versions) {}

    private final List<VersionRepository> repos;
    private final Duration ttl;
    private final Supplier<Long> clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ConcurrentHashMap<Gav, Entry> cache = new ConcurrentHashMap<>();
    private final VersionComparator comparator = new VersionComparator();

    public VersionService() {
        this(load(), Duration.ofMinutes(30), System::currentTimeMillis);
    }

    /** @return repositories registered by service loader */
    static List<VersionRepository> load() {
        // explicit class loader, the plugin class loader is not the context one in the IDE
        return ServiceLoader.load(VersionRepository.class, VersionService.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get).toList();
    }

    public VersionService(List<VersionRepository> repos, Duration ttl, Supplier<Long> clock) {
        this.repos = List.copyOf(repos);
        this.ttl = ttl;
        this.clock = clock;
    }

    /** @return versions sorted newest first. blocking, call it from a background thread. */
    public List<String> versions(Gav gav) throws IOException {
        Entry e = cache.get(gav);
        if (e != null && clock.get() - e.time < ttl.toMillis()) return e.versions;
        List<String> v = fetch(gav);
        cache.put(gav, new Entry(clock.get(), v));
        return v;
    }

    /** whether the cache is available and not expired */
    public boolean isFresh(Gav gav) {
        Entry e = cache.get(gav);
        return e != null && clock.get() - e.time < ttl.toMillis();
    }

    /** @return cached versions even if expired, never touches the network */
    public Optional<List<String>> cached(Gav gav) {
        return Optional.ofNullable(cache.get(gav)).map(Entry::versions);
    }

    /**
     * asks all applicable repositories in parallel and merges. Errors are checked loosely:
     * 4xx and failure of a repository are ignored as long as another one answers.
     * It fails only when every repository failed (not 404) and nothing was found.
     */
    private List<String> fetch(Gav gav) throws IOException {
        List<CompletableFuture<List<String>>> fs = repos.stream().filter(r -> r.applies(gav))
                .map(r -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetch(r, gav);
                    } catch (IOException ex) {
                        throw new CompletionException(ex);
                    }
                })).toList();
        Set<String> all = new LinkedHashSet<>();
        IOException error = null;
        for (var f : fs) {
            try {
                all.addAll(f.join());
            } catch (CompletionException ex) {
                if (ex.getCause() instanceof IOException io) error = io; else throw ex;
            }
        }
        if (all.isEmpty() && error != null) throw error;
        List<String> r = new ArrayList<>(all);
        r.sort(comparator.reversed());
        return List.copyOf(r);
    }

    /** @return empty if not found (4xx) */
    private List<String> fetch(VersionRepository repo, Gav gav) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(repo.url() + gav.metadataPath()))
                .timeout(repo.timeout()).GET().build();
        IOException last = null;
        for (int i = 0; i <= repo.retries(); i++) {
            try {
                HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = res.body()) {
                    // jitpack answers 401 for unknown artifacts, so any 4xx just means "not here"
                    if (res.statusCode() >= 400 && res.statusCode() < 500) return List.of();
                    if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + ": " + req.uri());
                    return MetadataParser.parseVersions(in);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } catch (IOException e) {
                last = e; // retry
            }
        }
        throw last;
    }

    /**
     * @param current the current version
     * @return the newest version greater than current. a stable current only compares to stable versions.
     */
    public Optional<String> newer(Gav gav, String current, List<String> versions) {
        boolean stable = VersionComparator.isStable(current);
        return versions.stream()
                .filter(v -> !stable || VersionComparator.isStable(v))
                .filter(v -> comparator.compare(v, current) > 0)
                .findFirst(); // sorted newest first
    }

    /** forgets the cache of the artifact to force refetching */
    public void clear(Gav gav) {
        cache.remove(gav);
    }

    public void clear() {
        cache.clear();
    }
}
