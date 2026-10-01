/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.util.List;
import java.util.Optional;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import vavi.idea.plugin.maven.core.Gav;
import vavi.idea.plugin.maven.core.VersionService;


/**
 * NewerVersionInspection, highlights version tags which have newer versions.
 * <p>
 * Inspections run in a read action, so this only reads the cache and asks the holder to fetch in the background.
 * Highlighting is restarted when the versions arrive. A failed fetch is silently ignored (offline etc.).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class NewerVersionInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!PomUtil.isPom(file)) return PsiElementVisitor.EMPTY_VISITOR;
        VersionServiceHolder holder_ = ApplicationManager.getApplication().getService(VersionServiceHolder.class);
        VersionService service = holder_.service();
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
                if (!PomUtil.isVersionTag(tag)) return;
                Gav gav = PomUtil.gav(tag);
                String current = PomUtil.literalVersion(tag);
                if (gav == null || current == null) return;
                // read action: cache only, never the network
                Optional<List<String>> versions = service.cached(gav);
                holder_.requestRefresh(gav, file.getProject());
                versions.flatMap(vs -> service.newer(gav, current, vs)).ifPresent(n ->
                        holder.registerProblem(tag.getValue().getChildren().length > 0 ? tag.getValue().getChildren()[0] : tag,
                                "Newer version available: " + n, ProblemHighlightType.WEAK_WARNING, new UpdateVersionFix(n)));
            }
        };
    }
}
