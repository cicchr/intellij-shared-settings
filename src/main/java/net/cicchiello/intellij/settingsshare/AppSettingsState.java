package net.cicchiello.intellij.settingsshare;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "net.cicchiello.intellij.settingsshare.AppSettingState",
        storages = @Storage("settingsShare.xml")
)
public class AppSettingsState implements PersistentStateComponent<AppSettingsState> {

    public String repositoryUrl;
    public String branch;
    public boolean enforceSettings = true;

    public static AppSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(AppSettingsState.class);
    }

    @Override
    public @Nullable AppSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull final AppSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
