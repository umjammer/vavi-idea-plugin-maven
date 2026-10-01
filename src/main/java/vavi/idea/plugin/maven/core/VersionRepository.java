/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core;

import java.time.Duration;


/**
 * VersionRepository, a maven repository to ask versions. Registered by {@link java.util.ServiceLoader}
 * (META-INF/services/vavi.idea.plugin.maven.core.VersionRepository).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public interface VersionRepository {

    /** base url, must end with "/" */
    String url();

    /** avoids useless requests */
    default boolean applies(Gav gav) {
        return true;
    }

    default Duration timeout() {
        return Duration.ofSeconds(10);
    }

    /** how many times retries on failure */
    default int retries() {
        return 0;
    }
}
