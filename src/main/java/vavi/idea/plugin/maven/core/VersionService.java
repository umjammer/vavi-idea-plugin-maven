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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * VersionService, fetches versions straight from maven-metadata.xml of a repository
 * (Maven Central serves it directly, so no database nor scraping is needed) with a TTL cache.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class VersionService {

    public static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    private record Entry(long time, List<String> versions) {}

    private final String baseUrl;
    private final Duration ttl;
    private final Supplier<Long> clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConcurrentHashMap<Gav, Entry> cache = new ConcurrentHashMap<>();
    private final VersionComparator comparator = new VersionComparator();

    public VersionService() {
        this(CENTRAL, Duration.ofMinutes(30), System::currentTimeMillis);
    }

    public VersionService(String baseUrl, Duration ttl, Supplier<Long> clock) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
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

    /** @return cached versions even if expired, never touches the network */
    public Optional<List<String>> cached(Gav gav) {
        return Optional.ofNullable(cache.get(gav)).map(Entry::versions);
    }

    private List<String> fetch(Gav gav) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + gav.metadataPath()))
                .timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + ": " + req.uri());
                List<String> r = new java.util.ArrayList<>(MetadataParser.parseVersions(in));
                r.sort(comparator.reversed());
                return List.copyOf(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
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
