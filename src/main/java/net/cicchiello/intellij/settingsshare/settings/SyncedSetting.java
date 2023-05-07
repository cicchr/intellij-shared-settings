package net.cicchiello.intellij.settingsshare.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.action.SettingChangedListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface SyncedSetting extends Disposable {

    @NonNull
    String getHumanName();

    @NonNull
    String getConfigName();

    @NonNull
    Optional<String> enforceSetting(@NonNull final String settingValue, @NonNull final Project project);

    void addSettingChangedListener(@NonNull final Project project, @NonNull final SettingChangedListener listener);

    @NonNull
    default List<String> updateProfilesFromPath(@NonNull final Path path, @NonNull final Project project) throws IOException {
        return List.of();
    }

}
