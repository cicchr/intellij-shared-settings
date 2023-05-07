package net.cicchiello.intellij.settingsshare.action;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.AppSettingsState;
import net.cicchiello.intellij.settingsshare.service.GitRepoService;
import net.cicchiello.intellij.settingsshare.service.ProfileUpdateService;
import net.cicchiello.intellij.settingsshare.settings.SyncedSetting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class EnforceSettingsOnStart implements ProjectActivity, SettingChangedListener {

    @Nullable
    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        if (AppSettingsState.getInstance().enforceSettings) {
            final SyncSettingsTask task = new SyncSettingsTask(ProjectManager.getInstance().getOpenProjects(), project, true);
            ApplicationManager.getApplication().invokeLater(() -> ProgressManager.getInstance().run(task));
        }
        ProfileUpdateService.getInstance().addEnforcementListeners(project, this);
        return null;
    }

    @Override
    public void settingChanged(@NonNull final SyncedSetting setting, @NonNull final Project project) {
        if (!AppSettingsState.getInstance().enforceSettings) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                final Optional<Path> maybePath = GitRepoService.getInstance().getRepositoryPath();
                if (maybePath.isEmpty()) {
                    return;
                }
                final Path path = maybePath.get();
                final List<String> enforcedProfiles = setting.updateProfilesFromPath(path, project);
                final Optional<String> maybeEnforced = ProfileUpdateService.getInstance().enforceSetting(setting, project, path);
                final StringBuilder sb = new StringBuilder("<b>Enforced modified settings</b>");
                if (!enforcedProfiles.isEmpty()) {
                    sb.append("<br/>Imported ").append(setting.getHumanName()).append(" profiles: <b>").append(StringUtils.join(enforcedProfiles, ", ")).append("</b>");
                }
                maybeEnforced.ifPresent(message -> sb.append("<br/>Enforced ").append(setting.getHumanName()).append(" setting: <b>").append(message).append("</b>"));
                if (maybeEnforced.isPresent() || !enforcedProfiles.isEmpty()) {
                    NotificationGroupManager.getInstance()
                            .getNotificationGroup("net.cicchiello.intellij.settingsshare")
                            .createNotification(sb.toString(), NotificationType.WARNING)
                            .addAction(new ShowSettingsAction())
                            .notify(project);
                }
            } catch (final IOException e) {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("net.cicchiello.intellij.settingsshare")
                        .createNotification(String.format("Failed to enforce setting %s: %s", setting.getHumanName(), e.getMessage()), NotificationType.ERROR)
                        .notify(project);
            }
        });
    }

}
