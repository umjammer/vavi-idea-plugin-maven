/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import vavi.idea.plugin.maven.core.Gav;


/**
 * PomUtil.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
final class PomUtil {

    private PomUtil() {}

    static boolean isPom(PsiFile file) {
        return file instanceof XmlFile x && x.getName().endsWith(".xml")
                && x.getRootTag() != null && "project".equals(x.getRootTag().getName());
    }

    /** whether the tag is a literal {@code <version>} of dependency, plugin, extension or parent */
    static boolean isVersionTag(XmlTag tag) {
        if (!"version".equals(tag.getName())) return false;
        XmlTag parent = tag.getParentTag();
        if (parent == null) return false;
        return switch (parent.getName()) {
            case "dependency", "plugin", "extension", "parent" -> true;
            default -> false;
        };
    }

    /** @return null if group or artifact is not resolvable (e.g. contains ${...}) */
    static Gav gav(XmlTag version) {
        XmlTag parent = version.getParentTag();
        if (parent == null) return null;
        String a = parent.getSubTagText("artifactId");
        String g = parent.getSubTagText("groupId");
        if (g == null && parent.getName().equals("plugin")) g = "org.apache.maven.plugins";
        if (g == null || a == null) return null;
        g = g.trim();
        a = a.trim();
        if (g.contains("${") || a.contains("${") || g.isEmpty() || a.isEmpty()) return null;
        return new Gav(g, a);
    }

    /** @return null if the version is not literal (property reference) */
    static String literalVersion(XmlTag version) {
        String v = version.getValue().getTrimmedText();
        return v.isEmpty() || v.contains("${") || v.startsWith("[") || v.startsWith("(") ? null : v;
    }
}
