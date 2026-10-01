/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import vavi.idea.plugin.maven.core.repository.JitpackRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * CoreTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
class CoreTest {

    @Test
    void testOrder() {
        VersionComparator c = new VersionComparator();
        assertTrue(c.compare("1.10", "1.9") > 0);
        assertTrue(c.compare("1.0-rc1", "1.0-beta2") > 0);
        assertTrue(c.compare("1.0", "1.0-rc1") > 0);
        assertTrue(c.compare("1.0-SNAPSHOT", "1.0") < 0);
        assertEquals(0, c.compare("1.0", "1.0.0"));
        assertTrue(c.compare("1.0.1", "1.0") > 0);
        assertTrue(c.compare("1.0-sp1", "1.0") > 0);
    }

    @Test
    void testStable() {
        assertTrue(VersionComparator.isStable("6.1.1"));
        assertFalse(VersionComparator.isStable("6.2.0-M1"));
        assertFalse(VersionComparator.isStable("1.0-RC2"));
        assertFalse(VersionComparator.isStable("2.0.0-SNAPSHOT"));
        assertTrue(VersionComparator.isStable("1.1.24"));
    }

    @Test
    void testParse() throws Exception {
        String xml = """
                <metadata><groupId>g</groupId><artifactId>a</artifactId><version>9</version>
                <versioning><latest>2</latest><versions><version>1</version><version>2</version></versions></versioning></metadata>""";
        assertEquals(List.of("1", "2"), MetadataParser.parseVersions(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void testNewer() {
        VersionService s = new VersionService();
        List<String> vs = List.of("3.0-M1", "2.1", "2.0", "1.0");
        assertEquals("2.1", s.newer(new Gav("g", "a"), "2.0", vs).orElseThrow());
        assertEquals("3.0-M1", s.newer(new Gav("g", "a"), "3.0-M0", vs).orElseThrow());
        assertTrue(s.newer(new Gav("g", "a"), "2.1", vs).isEmpty());
    }

    @Test
    void testMetadataPath() {
        assertEquals("org/junit/junit-bom/maven-metadata.xml", new Gav("org.junit", "junit-bom").metadataPath());
    }

    @Test
    void testJitpackStable() {
        assertFalse(VersionComparator.isStable("-8cacd12725-1"));
        assertFalse(VersionComparator.isStable("main-SNAPSHOT"));
    }

    @Test
    void testJitpackApplies() {
        VersionRepository r = new JitpackRepository();
        assertTrue(r.applies(new Gav("com.github.umjammer", "vavi-commons")));
        assertFalse(r.applies(new Gav("org.junit", "junit-bom")));
    }

    @Test
    void testServiceLoader() {
        List<String> urls = VersionService.load().stream().map(VersionRepository::url).toList();
        assertTrue(urls.contains("https://repo.maven.apache.org/maven2/"));
        assertTrue(urls.contains("https://jitpack.io/"));
    }

    /** real network, run with -Dnet=true */
    @Test
    @EnabledIfSystemProperty(named = "net", matches = "true")
    void testCentral() throws Exception {
        VersionService s = new VersionService();
        Gav gav = new Gav("org.junit.jupiter", "junit-jupiter-api");
        assertTrue(s.versions(gav).size() > 10);
        assertTrue(s.cached(gav).isPresent());
        // jitpack only
        assertTrue(s.versions(new Gav("com.github.umjammer", "vavi-commons")).contains("1.1.24"));
        // unknown artifact: 404 everywhere is not an error
        assertTrue(s.versions(new Gav("com.github.umjammer", "no-such-artifact-xyz")).isEmpty());
    }
}
