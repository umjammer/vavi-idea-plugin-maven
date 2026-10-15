/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactNode;
import org.jetbrains.idea.maven.model.MavenArtifactState;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import vavi.idea.plugin.maven.core.Coordinate;
import vavi.idea.plugin.maven.core.Gav;
import vavi.idea.plugin.maven.core.VersionService;
import vavi.idea.plugin.maven.core.VulnerabilityService;


/**
 * VulnerableTransitiveInspection, highlights a dependency which brings transitive dependencies having known vulnerabilities,
 * and offers {@link ReplaceVulnerableTransitiveFix}.
 * <p>
 * The dependency tree is the one resolved by the IDE maven support, vulnerabilities come from OSV
 * and from the package checker ({@link PackageCheckerBridge}) so that the same dependencies as its markers are fixable.
 * Highlights the same {@code <artifactId>} as the "Vulnerability found in dependency" of the package checker,
 * so the quick fix also shows up in its popup.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-03 nsano initial version <br>
 */
public class VulnerableTransitiveInspection extends LocalInspectionTool {

    /** light gray, readable on both light and dark themes */
    private static final String ID_COLOR = "#999999";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        VirtualFile vf = file.getVirtualFile();
        if (!PomUtil.isPom(file) || vf == null) return PsiElementVisitor.EMPTY_VISITOR;
        MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(file.getProject());
        MavenProject mavenProject = manager == null ? null : manager.findProject(vf);
        if (mavenProject == null) return PsiElementVisitor.EMPTY_VISITOR;
        List<MavenArtifactNode> tree = mavenProject.getDependencyTree();
        VulnerabilityServiceHolder vulnerabilityHolder = ApplicationManager.getApplication().getService(VulnerabilityServiceHolder.class);
        VersionServiceHolder versionHolder = ApplicationManager.getApplication().getService(VersionServiceHolder.class);
        VulnerabilityService vulnerabilities = vulnerabilityHolder.service();
        VersionService versions = versionHolder.service();
        // the package checker runs once per file, lazily
        var packageChecker = new Object() { Map<XmlTag, Map<Coordinate, List<String>>> found; };
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
                if (!PomUtil.isDependencyTag(tag)) return;
                Gav gav = PomUtil.dependencyGav(tag);
                XmlTag artifactId = tag.findFirstSubTag("artifactId");
                if (gav == null || artifactId == null) return;
                Set<Coordinate> transitives = transitives(tree, gav);
                if (transitives.isEmpty()) return;
                // read action: cache only, never the network
                vulnerabilityHolder.requestRefresh(transitives, file.getProject());
                Map<Coordinate, List<String>> vulnerable = new LinkedHashMap<>();
                for (Coordinate c : transitives) {
                    vulnerabilities.cached(c).filter(ids -> !ids.isEmpty()).ifPresent(ids -> vulnerable.put(c, ids));
                }
                if (packageChecker.found == null) packageChecker.found = PackageCheckerBridge.vulnerabilities(file, isOnTheFly, allTransitives(file, tree));
                packageChecker.found.getOrDefault(tag, Map.of()).forEach((c, ids) -> vulnerable.merge(c, ids, (a, b) ->
                        Stream.concat(a.stream(), b.stream()).distinct().toList()));
                if (vulnerable.isEmpty()) return;
                List<ReplaceVulnerableTransitiveFix.Replacement> replacements = new ArrayList<>();
                for (Coordinate c : vulnerable.keySet()) {
                    replacement(c, versions, versionHolder, file).ifPresent(replacements::add);
                }
                String message = "Vulnerable transitive dependencies:\n" + vulnerable.entrySet().stream()
                        .map(e -> e.getKey() + (e.getValue().isEmpty() ? "" : " (" + String.join(", ", e.getValue()) + ")"))
                        .collect(Collectors.joining("\n"));
                // the popup shows the tooltip, the problems view the plain message
                String tooltip = "<html>Vulnerable transitive dependencies:<br>" + vulnerable.entrySet().stream()
                        .map(e -> XmlStringUtil.escapeString(e.getKey().toString()) + (e.getValue().isEmpty() ? "" : " <span style=\"color:" + ID_COLOR + "\">("
                                + XmlStringUtil.escapeString(String.join(", ", e.getValue())) + ")</span>"))
                        .collect(Collectors.joining("<br>")) + "</html>";
                PsiElement target = artifactId.getValue().getChildren().length > 0 ? artifactId.getValue().getChildren()[0] : artifactId;
                LocalQuickFix[] fixes = replacements.isEmpty() ? LocalQuickFix.EMPTY_ARRAY
                        : new LocalQuickFix[] {new ReplaceVulnerableTransitiveFix(replacements)};
                holder.registerProblem(new ProblemDescriptorBase(target, target, message, fixes,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, true, isOnTheFly, tooltip));
            }
        };
    }

    /**
     * @return an updated version of the vulnerable transitive dependency, or the newest one of its renamed artifact
     * if the artifact itself has no newer version. from the caches only, missing ones are requested.
     */
    private static Optional<ReplaceVulnerableTransitiveFix.Replacement> replacement(Coordinate c, VersionService versions,
                                                                                VersionServiceHolder versionHolder, PsiFile file) {
        versionHolder.requestRefresh(c.gav(), file.getProject());
        Optional<String> newer = versions.cached(c.gav()).flatMap(vs -> versions.newer(c.gav(), c.version(), vs));
        if (newer.isPresent()) {
            return newer.map(v -> new ReplaceVulnerableTransitiveFix.Replacement(c.gav(), new Coordinate(c.groupId(), c.artifactId(), v)));
        }
        if (versions.cached(c.gav()).isEmpty()) return Optional.empty(); // not fetched yet
        versionHolder.requestSuccessors(c.gav(), file.getProject());
        return versions.cachedSuccessor(c.gav(), c.version()).map(s -> new ReplaceVulnerableTransitiveFix.Replacement(c.gav(), s));
    }

    /** @return transitive dependencies of each dependency tag in the file */
    private static Map<XmlTag, Set<Coordinate>> allTransitives(PsiFile file, List<MavenArtifactNode> tree) {
        Map<XmlTag, Set<Coordinate>> r = new HashMap<>();
        for (XmlTag t : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
            if (!PomUtil.isDependencyTag(t)) continue;
            Gav gav = PomUtil.dependencyGav(t);
            if (gav != null) r.put(t, transitives(tree, gav));
        }
        return r;
    }

    /**
     * @return transitive dependencies (not the dependency itself) of the dependency actually resolved through it.
     * conflicted or duplicated ones are resolved through another path, excluding them here changes nothing.
     */
    static Set<Coordinate> transitives(List<MavenArtifactNode> tree, Gav gav) {
        Set<Coordinate> r = new LinkedHashSet<>();
        for (MavenArtifactNode node : tree) {
            MavenArtifact a = node.getArtifact();
            if (a.getGroupId().equals(gav.groupId()) && a.getArtifactId().equals(gav.artifactId())) {
                collect(node.getDependencies(), r);
            }
        }
        return r;
    }

    private static void collect(List<MavenArtifactNode> nodes, Set<Coordinate> r) {
        for (MavenArtifactNode node : nodes) {
            if (node.getState() != MavenArtifactState.ADDED) continue;
            MavenArtifact a = node.getArtifact();
            r.add(new Coordinate(a.getGroupId(), a.getArtifactId(), Optional.ofNullable(a.getBaseVersion()).orElse(a.getVersion())));
            collect(node.getDependencies(), r);
        }
    }
}
