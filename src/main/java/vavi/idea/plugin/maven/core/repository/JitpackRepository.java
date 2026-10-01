/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core.repository;

import java.time.Duration;

import vavi.idea.plugin.maven.core.Gav;
import vavi.idea.plugin.maven.core.VersionRepository;


/**
 * JitpackRepository. jitpack is slow and unstable, so it gets a long timeout and retries.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class JitpackRepository implements VersionRepository {

    @Override
    public String url() {
        return "https://jitpack.io/";
    }

    /** jitpack only serves git hosting groups */
    @Override
    public boolean applies(Gav gav) {
        String g = gav.groupId();
        return g.startsWith("com.github.") || g.startsWith("com.gitlab.")
                || g.startsWith("org.bitbucket.") || g.startsWith("com.gitee.");
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    public int retries() {
        return 2;
    }
}
