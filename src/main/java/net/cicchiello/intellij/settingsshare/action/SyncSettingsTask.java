package net.cicchiello.intellij.settingsshare.action;

import net.cicchiello.intellij.settingsshare.AppSettingsState;
import net.cicchiello.intellij.settingsshare.service.GitRepoService;
import net.cicchiello.intellij.settingsshare.service.ProfileUpdateService;
import com.google.common.collect.Multimap;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.settings.SyncedSetting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;

public class SyncSettingsTask extends Task.Backgroundable {

    private final Project[] projects;
    private final boolean quiet;

    public SyncSettingsTask(@NonNull final Project[] projects, @NonNull final Project currentProject, final boolean quiet) {
        super(currentProject, "Pulling shared settings", false);
        this.projects = projects;
        this.quiet = quiet;
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
        final AppSettingsState state = AppSettingsState.getInstance();
        if (state.repositoryUrl == null || state.branch == null) {
            return;
        }

        final GitRepoService gitService = GitRepoService.getInstance();
        final ProfileUpdateService profileService = ProfileUpdateService.getInstance();
        try {

            final Path gitPath = gitService.updateAndCheckoutBranch(getProject(), state.repositoryUrl, state.branch);
            final Multimap<SyncedSetting, String> importedProfiles = profileService.importGlobalProfilesFromPath(gitPath, getProject());

            for (final Project project : projects) {
                final Map<SyncedSetting, String> settingsEnforced = profileService.enforceSettings(gitPath, project);

                sendNotification(project, importedProfiles, settingsEnforced);
            }
        } catch (final Exception e) {
            e.printStackTrace();
            final String errorMessage = String.format("Failed to sync shared settings: %s", e.getMessage());
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("net.cicchiello.intellij.settingsshare")
                    .createNotification(errorMessage, NotificationType.ERROR)
                    .notify(getProject());
        }
    }

    private void sendNotification(final Project project, final Multimap<SyncedSetting, String> updatedProfiles, final Map<SyncedSetting, String> profilesChanged) {
        if ((quiet || project != getProject()) && updatedProfiles.isEmpty() && profilesChanged.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder("<b>Shared settings synchronized</b>");

        updatedProfiles.asMap().forEach((setting, profiles) -> sb.append("<br/>Updated ").append(setting.getHumanName()).append(" profiles: <b>").append(StringUtils.join(profiles, ", ")).append("</b>"));
        profilesChanged.forEach((setting, message) -> sb.append("<br/>Enforced ").append(setting.getHumanName()).append(" setting: <b>").append(message).append("</b>"));

        if (updatedProfiles.isEmpty() && profilesChanged.isEmpty()) {
            sb.append("<br/>No changes!");
        }

        NotificationGroupManager.getInstance()
                .getNotificationGroup("net.cicchiello.intellij.settingsshare")
                .createNotification(sb.toString(), NotificationType.INFORMATION)
                .notify(project);
    }
}
