/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
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
 * Inspections run on background threads, so fetching here never blocks the UI.
 * A failed fetch is silently ignored (offline etc.).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class NewerVersionInspection extends LocalInspectionTool {

    private static final Logger LOG = Logger.getInstance(NewerVersionInspection.class);

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!PomUtil.isPom(file)) return PsiElementVisitor.EMPTY_VISITOR;
        VersionService service = ApplicationManager.getApplication().getService(VersionServiceHolder.class).service();
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(@NotNull XmlTag tag) {
                if (!PomUtil.isVersionTag(tag)) return;
                Gav gav = PomUtil.gav(tag);
                String current = PomUtil.literalVersion(tag);
                if (gav == null || current == null) return;
                ProgressManager.checkCanceled();
                try {
                    List<String> versions = service.versions(gav);
                    Optional<String> newer = service.newer(gav, current, versions);
                    newer.ifPresent(n -> holder.registerProblem(tag.getValue().getChildren().length > 0 ? tag.getValue().getChildren()[0] : tag,
                            "Newer version available: " + n, ProblemHighlightType.WEAK_WARNING, new UpdateVersionFix(n)));
                } catch (IOException e) {
                    LOG.debug("cannot fetch versions of " + gav, e);
                }
            }
        };
    }
}
