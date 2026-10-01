/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core;


/**
 * Gav, a maven artifact coordinate without version.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public record Gav(String groupId, String artifactId) {

    /** relative path of maven-metadata.xml in a repository */
    public String metadataPath() {
        return groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
    }
}
