/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
 * The dependency tree is the one resolved by the IDE maven support, vulnerabilities come from OSV.
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
                if (vulnerable.isEmpty()) return;
                List<ReplaceVulnerableTransitiveFix.Replacement> replacements = new ArrayList<>();
                for (Coordinate c : vulnerable.keySet()) {
                    versionHolder.requestRefresh(c.gav(), file.getProject());
                    versions.cached(c.gav()).flatMap(vs -> versions.newer(c.gav(), c.version(), vs)).ifPresent(v ->
                            replacements.add(new ReplaceVulnerableTransitiveFix.Replacement(c.groupId(), c.artifactId(), v)));
                }
                String message = "Vulnerable transitive dependencies:\n" + vulnerable.entrySet().stream()
                        .map(e -> e.getKey() + " (" + String.join(", ", e.getValue()) + ")")
                        .collect(Collectors.joining("\n"));
                // the popup shows the tooltip, the problems view the plain message
                String tooltip = "<html>Vulnerable transitive dependencies:<br>" + vulnerable.entrySet().stream()
                        .map(e -> XmlStringUtil.escapeString(e.getKey().toString()) + " <span style=\"color:" + ID_COLOR + "\">("
                                + XmlStringUtil.escapeString(String.join(", ", e.getValue())) + ")</span>")
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
