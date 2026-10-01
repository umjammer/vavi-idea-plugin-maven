/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.io.IOException;
import java.util.List;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import vavi.idea.plugin.maven.core.Gav;
import vavi.idea.plugin.maven.core.VersionService;


/**
 * ShowVersionsAction, shows the latest version list on a version tag.
 * <p>
 * Bound to cmd+/ but enabled only on a pom version tag, otherwise the key goes to the usual "comment" action.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class ShowVersionsAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static XmlTag versionTag(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null || !PomUtil.isPom(file)) return null;
        var at = file.findElementAt(editor.getCaretModel().getOffset());
        XmlTag tag = PsiTreeUtil.getParentOfType(at, XmlTag.class, false);
        return tag != null && PomUtil.isVersionTag(tag) && PomUtil.gav(tag) != null ? tag : null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(versionTag(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        XmlTag tag = versionTag(e);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();
        if (tag == null || editor == null || project == null) return;
        Gav gav = PomUtil.gav(tag);
        VersionService service = ApplicationManager.getApplication().getService(VersionServiceHolder.class).service();
        var pointer = com.intellij.openapi.application.ReadAction.compute(
                () -> com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag));
        // always asks the repository (no stale list); falls back to the cache when offline
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<String> versions;
            try {
                service.clear(gav);
                versions = service.versions(gav);
            } catch (IOException ex) {
                versions = service.cached(gav).orElse(null);
                if (versions == null) {
                    NotificationGroupManager.getInstance().getNotificationGroup("vavi.maven")
                            .createNotification("Cannot fetch versions of " + gav.groupId() + ":" + gav.artifactId() + ": " + ex.getMessage(),
                                    NotificationType.WARNING).notify(project);
                    return;
                }
            }
            List<String> list = versions;
            ApplicationManager.getApplication().invokeLater(() ->
                    JBPopupFactory.getInstance().createPopupChooserBuilder(list)
                            .setTitle(gav.artifactId() + " versions")
                            .setItemChosenCallback(v -> WriteCommandAction.runWriteCommandAction(project, "Change Version", null, () -> {
                                XmlTag t = pointer.getElement();
                                if (t == null) return;
                                t.getValue().setText(v);
                                PsiDocumentManager.getInstance(project).commitAllDocuments();
                            }, t0(pointer)))
                            .createPopup().showInBestPositionFor(editor));
        });
    }

    private static PsiFile[] t0(com.intellij.psi.SmartPsiElementPointer<XmlTag> p) {
        PsiFile f = p.getContainingFile();
        return f == null ? new PsiFile[0] : new PsiFile[] {f};
    }
}
