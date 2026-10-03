/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.util.List;
import java.util.Set;

import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.model.MavenArtifactState;
import org.junit.jupiter.api.Test;
import vavi.idea.plugin.maven.core.Coordinate;
import vavi.idea.plugin.maven.core.Gav;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * VulnerableTransitiveInspectionTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-03 nsano initial version <br>
 */
class VulnerableTransitiveInspectionTest {

    private static MavenArtifactNode node(MavenArtifactNode parent, String g, String a, String v, MavenArtifactState state, MavenArtifactNode... children) {
        MavenArtifact artifact = new MavenArtifact(g, a, v, v, "jar", null, "compile", false, "jar", null, null, true, false);
        MavenArtifactNode n = new MavenArtifactNode(parent, artifact, state, null, "compile", null, null);
        n.setDependencies(List.of(children));
        return n;
    }

    @Test
    void testTransitives() {
        MavenArtifactNode root = node(null, "com.github.umjammer", "vavi-awt", "1.0.11", MavenArtifactState.ADDED,
                node(null, "org.apache.ant", "ant", "1.10.15", MavenArtifactState.ADDED,
                        node(null, "org.apache.ant", "ant-launcher", "1.10.15", MavenArtifactState.ADDED)),
                node(null, "org.jsoup", "jsoup", "1.21.2", MavenArtifactState.CONFLICT),
                node(null, "tools.jackson.core", "jackson-core", "3.1.1", MavenArtifactState.DUPLICATE));
        MavenArtifactNode other = node(null, "org.junit.jupiter", "junit-jupiter-api", "6.1.3", MavenArtifactState.ADDED,
                node(null, "org.opentest4j", "opentest4j", "1.3.0", MavenArtifactState.ADDED));

        Set<Coordinate> r = VulnerableTransitiveInspection.transitives(List.of(other, root), new Gav("com.github.umjammer", "vavi-awt"));
        assertEquals(Set.of(new Coordinate("org.apache.ant", "ant", "1.10.15"), new Coordinate("org.apache.ant", "ant-launcher", "1.10.15")), r);
    }
}
