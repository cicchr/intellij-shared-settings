package net.cicchiello.intellij.settingsshare.action;

import com.intellij.openapi.project.Project;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.settings.SyncedSetting;

public interface SettingChangedListener {

    void settingChanged(@NonNull final SyncedSetting setting, @NonNull final Project project);

}
