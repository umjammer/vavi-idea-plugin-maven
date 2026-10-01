/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import vavi.idea.plugin.maven.core.Gav;
import vavi.idea.plugin.maven.core.VersionService;


/**
 * VersionServiceHolder, application wide shared cache.
 * <p>
 * Inspections run in a read action, a long read action blocks write actions and freezes the UI,
 * so they must never touch the network. They use {@link #requestRefresh} instead.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
@Service(Service.Level.APP)
public final class VersionServiceHolder {

    private static final Logger LOG = Logger.getInstance(VersionServiceHolder.class);

    /** do not retry the same artifact within this period even if failed */
    private static final long RETRY_INTERVAL = 5 * 60 * 1000;

    private final VersionService service = new VersionService();
    private final ConcurrentHashMap<Gav, Long> attempts = new ConcurrentHashMap<>();

    public VersionService service() {
        return service;
    }

    /** fetches in the background if the cache is missing or expired, then restarts highlighting. returns immediately. */
    public void requestRefresh(Gav gav, Project project) {
        if (service.isFresh(gav)) return;
        long now = System.currentTimeMillis();
        Long last = attempts.get(gav);
        if (last != null && now - last < RETRY_INTERVAL) return;
        if (attempts.put(gav, now) != null && last != null && now - last < RETRY_INTERVAL) return; // lost the race
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                service.versions(gav);
            } catch (IOException e) {
                LOG.debug("cannot fetch versions of " + gav, e);
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) DaemonCodeAnalyzer.getInstance(project).restart("vavi maven versions");
            });
        });
    }
}
