/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ProblemDescriptorUserDataKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import vavi.idea.plugin.maven.core.Coordinate;


/**
 * PackageCheckerBridge, vulnerable transitive dependencies found by the bundled package checker
 * ("Vulnerable declared dependency", the yellow marker on a dependency), which has its own data (Mend.io) besides OSV.
 * <p>
 * The package checker has no public api. Its inspection is run through the inspection profile as a usual
 * {@link LocalInspectionTool} and its problems are read through the platform key {@link ProblemDescriptorUserDataKt#FINGERPRINT_DATA},
 * so no class of the package checker is referred. Nothing is found if the package checker is not installed or is disabled.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-15 nsano initial version <br>
 */
final class PackageCheckerBridge {

    private static final Logger LOG = Logger.getInstance(PackageCheckerBridge.class);

    /** short name of {@code com.intellij.packageChecker.inspection.VulnerableLibrariesLocalInspection} */
    static final String SHORT_NAME = "VulnerableLibrariesLocal";

    private PackageCheckerBridge() {}

    /**
     * @param transitives transitive dependencies of each dependency tag, the found packages are matched with them
     * @return vulnerable transitive dependencies with vulnerability ids of each dependency tag. read action.
     */
    static Map<XmlTag, Map<Coordinate, List<String>>> vulnerabilities(PsiFile file, boolean isOnTheFly,
                                                                       Map<XmlTag, ? extends Collection<Coordinate>> transitives) {
        Map<XmlTag, Map<Coordinate, List<String>>> r = new HashMap<>();
        for (ProblemDescriptor d : problems(file, isOnTheFly)) {
            PsiElement e = d.getPsiElement();
            if (e == null) continue;
            XmlTag dependency = PsiTreeUtil.getParentOfType(e, XmlTag.class, false);
            while (dependency != null && !PomUtil.isDependencyTag(dependency)) dependency = dependency.getParentTag();
            Collection<Coordinate> candidates = dependency == null ? null : transitives.get(dependency);
            if (candidates == null) continue;
            Map<String, String> data = d instanceof UserDataHolder h ? h.getUserData(ProblemDescriptorUserDataKt.FINGERPRINT_DATA) : null;
            List<String> ids = data == null || data.get("vulnerabilityIds") == null ? List.of()
                    : Arrays.stream(data.get("vulnerabilityIds").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            String source = data == null ? null : data.get("sourcePackage");
            for (Coordinate c : matches(candidates, source, d.getDescriptionTemplate())) {
                List<String> merged = new ArrayList<>(r.computeIfAbsent(dependency, k -> new LinkedHashMap<>()).getOrDefault(c, List.of()));
                ids.stream().filter(id -> !merged.contains(id)).forEach(merged::add);
                r.get(dependency).put(c, List.copyOf(merged));
            }
        }
        return r;
    }

    /**
     * @param source "maven:group:artifact:version" of the package checker
     * @param description the message, which mentions the coordinates too
     * @return candidates which the package checker reports
     */
    static List<Coordinate> matches(Collection<Coordinate> candidates, String source, String description) {
        return candidates.stream().filter(c -> {
            String s = c.toString();
            return (source != null && (source.equals(s) || source.endsWith(":" + s)))
                    || (description != null && Pattern.compile("(?<![\\w.-])" + Pattern.quote(s) + "(?![\\w.-])").matcher(description).find());
        }).toList();
    }

    /** @return problems of the package checker inspection on the file, empty if it is unavailable */
    private static List<ProblemDescriptor> problems(PsiFile file, boolean isOnTheFly) {
        try {
            InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
            HighlightDisplayKey key = HighlightDisplayKey.find(SHORT_NAME);
            if (key == null || !profile.isToolEnabled(key, file)) return List.of();
            if (!(profile.getUnwrappedTool(SHORT_NAME, file) instanceof LocalInspectionTool tool)) return List.of();
            ProblemDescriptor[] r = tool.checkFile(file, InspectionManager.getInstance(file.getProject()), isOnTheFly);
            return r == null ? List.of() : List.of(r);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("package checker is not available", e);
            return List.of();
        }
    }
}
