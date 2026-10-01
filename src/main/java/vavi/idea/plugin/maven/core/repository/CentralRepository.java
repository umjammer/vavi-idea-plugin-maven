/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core.repository;


import vavi.idea.plugin.maven.core.VersionRepository;


/**
 * CentralRepository, Maven Central.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class CentralRepository implements VersionRepository {

    @Override
    public String url() {
        return "https://repo.maven.apache.org/maven2/";
    }
}
