/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import com.intellij.openapi.components.Service;
import vavi.idea.plugin.maven.core.VersionService;


/**
 * VersionServiceHolder, application wide shared cache.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
@Service(Service.Level.APP)
public final class VersionServiceHolder {

    private final VersionService service = new VersionService();

    public VersionService service() {
        return service;
    }
}
