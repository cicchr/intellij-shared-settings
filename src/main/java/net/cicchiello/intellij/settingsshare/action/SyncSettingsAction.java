package net.cicchiello.intellij.settingsshare.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import net.cicchiello.intellij.settingsshare.AppSettingsState;
import org.jetbrains.annotations.NotNull;

public class SyncSettingsAction extends AnAction {

    @Override
    public void update(@NotNull final AnActionEvent e) {
        final AppSettingsState state = AppSettingsState.getInstance();
        e.getPresentation().setEnabled(state.branch != null
                && state.repositoryUrl != null
                && e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        final Project project = event.getProject();
        if (project == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            final SyncSettingsTask task = new SyncSettingsTask(ProjectManager.getInstance().getOpenProjects(), project, false);
            ProgressManager.getInstance().run(task);
        });
    }

}
