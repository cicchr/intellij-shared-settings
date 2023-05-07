package net.cicchiello.intellij.settingsshare.service;

import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.action.SettingChangedListener;
import net.cicchiello.intellij.settingsshare.settings.SyncedSetting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public interface ProfileUpdateService {

    static ProfileUpdateService getInstance() {
        return ApplicationManager.getApplication().getService(ProfileUpdateService.class);
    }

    Multimap<SyncedSetting, String> importGlobalProfilesFromPath(@NonNull final Path path, @NonNull final Project project) throws IOException;

    Map<SyncedSetting, String> enforceSettings(@NonNull final Path path, @NonNull final Project project) throws IOException;

    Optional<String> enforceSetting(@NonNull SyncedSetting setting, @NonNull Project project, @NonNull Path repoPath) throws IOException;

    void addEnforcementListeners(@NonNull final Project project, @NonNull final SettingChangedListener listener);
}
