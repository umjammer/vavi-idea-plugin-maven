/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.idea.plugin.maven.ide;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vavi.idea.plugin.maven.core.Gav;
import vavi.idea.plugin.maven.core.VersionService;


/**
 * PrewarmActivity, fetches versions of all poms in the project when the project is opened,
 * and restarts the highlighting when they arrive.
 * <p>
 * The inspection itself fetches lazily, this only makes the first highlighting quick.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-10-01 nsano initial version <br>
 */
public class PrewarmActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(PrewarmActivity.class);

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> prewarm(project));
        return Unit.INSTANCE;
    }

    private void prewarm(Project project) {
        VersionService service = ApplicationManager.getApplication().getService(VersionServiceHolder.class).service();
        Set<Gav> gavs = DumbService.getInstance(project).runReadActionInSmartMode(() -> {
            Set<Gav> r = new HashSet<>();
            for (var vf : FilenameIndex.getVirtualFilesByName("pom.xml", GlobalSearchScope.projectScope(project))) {
                if (PsiManager.getInstance(project).findFile(vf) instanceof XmlFile x && PomUtil.isPom(x)) {
                    for (XmlTag t : PsiTreeUtil.findChildrenOfType(x, XmlTag.class)) {
                        if (PomUtil.isVersionTag(t) && PomUtil.literalVersion(t) != null) {
                            Gav gav = PomUtil.gav(t);
                            if (gav != null) r.add(gav);
                        }
                    }
                }
            }
            return r;
        });
        for (Gav gav : gavs) {
            if (project.isDisposed()) return;
            try {
                service.versions(gav);
            } catch (IOException e) {
                LOG.debug("prewarm " + gav, e);
            }
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) DaemonCodeAnalyzer.getInstance(project).restart("vavi maven prewarm");
        });
    }
}
