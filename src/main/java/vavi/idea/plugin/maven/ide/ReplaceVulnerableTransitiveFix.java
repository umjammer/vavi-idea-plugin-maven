/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.util.List;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import vavi.idea.plugin.maven.core.Coordinate;
import vavi.idea.plugin.maven.core.Gav;


/**
 * ReplaceVulnerableTransitiveFix, excludes vulnerable transitive dependencies from a dependency
 * and adds updated versions of them as direct dependencies just after it.
 * <pre>
 * &lt;dependency&gt;
 *   ...
 *   &lt;exclusions&gt;
 *     &lt;exclusion&gt;&lt;groupId&gt;g&lt;/groupId&gt;&lt;artifactId&gt;a&lt;/artifactId&gt;&lt;/exclusion&gt;
 *   &lt;/exclusions&gt;
 * &lt;/dependency&gt;
 * &lt;dependency&gt;&lt;groupId&gt;g&lt;/groupId&gt;&lt;artifactId&gt;a&lt;/artifactId&gt;&lt;version&gt;newer&lt;/version&gt;&lt;/dependency&gt;
 * </pre>
 * The added one may be a renamed artifact (e.g. {@code jetty-websocket-core-common} for {@code websocket-core-common})
 * when the vulnerable one has no newer version.
 * Already existing exclusions and dependencies are left as they are.
 * The maven project is synced after the fix and also after its undo / redo.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-03 nsano initial version <br>
 */
class ReplaceVulnerableTransitiveFix implements LocalQuickFix, PriorityAction {

    /**
     * @param excluded a vulnerable transitive dependency
     * @param added an updated version of it, or of its renamed artifact
     */
    record Replacement(Gav excluded, Coordinate added) {

        /** for the fix name */
        String label() {
            return excluded.artifactId().equals(added.artifactId()) ? excluded.artifactId() + " with " + added.version()
                    : excluded.artifactId() + " with " + added.artifactId() + " " + added.version();
        }
    }

    private final List<Replacement> replacements;

    ReplaceVulnerableTransitiveFix(List<Replacement> replacements) {
        this.replacements = List.copyOf(replacements);
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Replace vulnerable transitive dependencies with updated versions";
    }

    @Override
    public @NotNull String getName() {
        return replacements.size() == 1
                ? "Replace vulnerable transitive " + replacements.getFirst().label()
                : "Replace " + replacements.size() + " vulnerable transitive dependencies with updated versions";
    }

    /**
     * the popup shows only the first fix. HIGH is enough to precede "Ignore vulnerable dependency" (NORMAL)
     * of the package checker, and stays equal to its version update fix (HIGH). an intentions order provider
     * (e.g. usage based ordering) can still reorder this, priority only matters in the default order.
     */
    @Override
    public @NotNull Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        XmlTag dependency = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlTag.class, false);
        while (dependency != null && !PomUtil.isDependencyTag(dependency)) dependency = dependency.getParentTag();
        if (dependency == null) return;
        XmlTag dependencies = dependency.getParentTag();
        String ns = dependency.getNamespace();
        String scope = dependency.getSubTagText("scope");

        XmlTag exclusions = dependency.findFirstSubTag("exclusions");
        if (exclusions == null) exclusions = dependency.addSubTag(dependency.createChildTag("exclusions", ns, null, false), false);
        PsiElement anchor = dependency;
        for (Replacement r : replacements) {
            if (find(exclusions, "exclusion", r.excluded()) == null) {
                XmlTag exclusion = exclusions.addSubTag(exclusions.createChildTag("exclusion", ns, null, false), false);
                exclusion.addSubTag(exclusion.createChildTag("groupId", ns, r.excluded().groupId(), false), false);
                exclusion.addSubTag(exclusion.createChildTag("artifactId", ns, r.excluded().artifactId(), false), false);
            }
            if (dependencies != null && find(dependencies, "dependency", r.added().gav()) == null) {
                XmlTag added = (XmlTag) dependencies.addAfter(dependencies.createChildTag("dependency", ns, null, false), anchor);
                added.addSubTag(added.createChildTag("groupId", ns, r.added().groupId(), false), false);
                added.addSubTag(added.createChildTag("artifactId", ns, r.added().artifactId(), false), false);
                added.addSubTag(added.createChildTag("version", ns, r.added().version(), false), false);
                if (scope != null && !scope.isBlank()) added.addSubTag(added.createChildTag("scope", ns, scope.trim(), false), false);
                anchor = added;
            }
        }
        if (dependencies != null) {
            CodeStyleManager.getInstance(project).reformatRange(dependencies,
                    dependency.getTextRange().getStartOffset(), anchor.getTextRange().getEndOffset());
        } else {
            CodeStyleManager.getInstance(project).reformat(dependency);
        }

        PsiFile file = dependency.getContainingFile();
        if (!file.isPhysical()) return; // intention preview
        VirtualFile vf = file.getVirtualFile();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (vf == null || document == null) return;
        // the inspection looks at the dependency tree of the last maven sync, not at the pom text.
        // without syncing, undo restores the text but the tree stays without the excluded ones and nothing is highlighted any more.
        UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(document) {
            @Override
            public void undo() {
                sync(project, vf);
            }

            @Override
            public void redo() {
                sync(project, vf);
            }
        });
        sync(project, vf);
    }

    /** syncs the maven project of the pom after the current command (undo included) finished */
    private static void sync(Project project, VirtualFile pom) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            FileDocumentManager.getInstance().saveAllDocuments(); // maven reads the pom from the disk
            MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
            MavenProject mavenProject = manager.findProject(pom);
            if (mavenProject != null) manager.forceUpdateProjects(List.of(mavenProject));
        });
    }

    /** @return a sub tag of parent named name which has the same group and artifact as gav */
    private static XmlTag find(XmlTag parent, String name, Gav gav) {
        for (XmlTag t : parent.findSubTags(name)) {
            String g = t.getSubTagText("groupId"), a = t.getSubTagText("artifactId");
            if (g != null && a != null && g.trim().equals(gav.groupId()) && a.trim().equals(gav.artifactId())) return t;
        }
        return null;
    }
}
