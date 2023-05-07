package net.cicchiello.intellij.settingsshare.action;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import net.cicchiello.intellij.settingsshare.PluginSettings;
import org.jetbrains.annotations.NotNull;

public class ShowSettingsAction extends NotificationAction {

    public ShowSettingsAction() {
        super("Configure shared settings enforcement");
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e, @NotNull final Notification notification) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), PluginSettings.class);
    }
}
