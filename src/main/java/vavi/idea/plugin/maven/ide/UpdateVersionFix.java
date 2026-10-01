/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;


/**
 * UpdateVersionFix.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
class UpdateVersionFix implements LocalQuickFix {

    private final String version;

    UpdateVersionFix(String version) {
        this.version = version;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Update version";
    }

    @Override
    public @NotNull String getName() {
        return "Update version to " + version;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement e = descriptor.getPsiElement();
        XmlTag tag = e instanceof XmlTag t ? t : com.intellij.psi.util.PsiTreeUtil.getParentOfType(e, XmlTag.class);
        if (tag != null) tag.getValue().setText(version);
    }
}
