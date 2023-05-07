package net.cicchiello.intellij.settingsshare.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSettingsLoader;
import com.intellij.util.containers.ContainerUtil;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.action.SettingChangedListener;
import org.apache.commons.compress.utils.FileNameUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class CodeStyleSyncedSetting extends ProfileSyncedSetting implements Disposable {

    public static final String CODESTYLE_PROFILE_FOLDER = "codestyles";
    public static final String CODESTYLE_SETTING_NAME = "CodeStyle";

    @NonNull
    @Override
    public String getHumanName() {
        return CODESTYLE_SETTING_NAME;
    }

    @NonNull
    @Override
    public String getConfigName() {
        return CODESTYLE_PROFILE_FOLDER;
    }

    @NonNull
    @Override
    public Optional<String> enforceSetting(@NonNull final String profileName, @NonNull final Project project) {
        final CodeStyleSchemes codeStyleSchemes = CodeStyleSchemes.getInstance();
        final Optional<CodeStyleScheme> scheme = codeStyleSchemes.getAllSchemes().stream()
                .filter(s -> profileName.equals(s.getName()))
                .findAny();
        final CodeStyleSettingsManager projectManager = CodeStyleSettingsManager.getInstance(project);
        if (scheme.isPresent() && (!codeStyleSchemes.getCurrentScheme().getName().equals(profileName) || projectManager.USE_PER_PROJECT_SETTINGS)) {
            setCodeStyleScheme(scheme.get(), project);
            return Optional.of(profileName);
        }
        return Optional.empty();
    }

    @Override
    public void addSettingChangedListener(final @NonNull Project project, final @NonNull SettingChangedListener listener) {
        project.getMessageBus().connect(this).subscribe(CodeStyleSettingsListener.TOPIC, (CodeStyleSettingsListener) event -> listener.settingChanged(this, project));
    }

    @NonNull
    @Override
    public Path getProfilePath(@NonNull final Path basePath) {
        return basePath.resolve(CODESTYLE_PROFILE_FOLDER);
    }

    @NonNull
    @Override
    public Optional<String> updateProfile(final @NonNull Path path, final Project project) throws IOException {
        final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance();
        final CodeStyleSchemes codeStyleSchemes = CodeStyleSchemes.getInstance();
        final String profileName = FileNameUtils.getBaseName(path.getFileName().toString());
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (file == null) {
            return Optional.empty();
        }
        file.refresh(false, false);
        try {
            final CodeStyleSchemeImpl newScheme = new CodeStyleSchemeImpl(profileName, false, null);
            final CodeStyleSettings newSettings = new CodeStyleSettingsLoader().loadSettings(file);
            newSettings.resetDeprecatedFields();
            newScheme.setCodeStyleSettings(newSettings);

            final Optional<CodeStyleSchemeImpl> existingScheme = codeStyleSchemes.getAllSchemes().stream()
                    .filter(s -> profileName.equals(s.getName()))
                    .map(s -> (CodeStyleSchemeImpl) s)
                    .findAny();

            final String newDom = JDOMUtil.write(newScheme.writeScheme());
            if (existingScheme.isEmpty() || !newDom.equals(JDOMUtil.write(existingScheme.get().writeScheme()))) {
                codeStyleSchemes.addScheme(newScheme);
                writeConfigFile(newDom, CODESTYLE_PROFILE_FOLDER, path.getFileName().toString());
                settingsManager.notifyCodeStyleSettingsChanged();
                return Optional.of(profileName);
            }
        } catch (final SchemeImportException e) {
            throw new IOException(String.format("Failed to load codestyle profile %s: %s", path.getFileName(), e.getMessage()), e);
        }
        return Optional.empty();
    }

    private void setCodeStyleScheme(final CodeStyleScheme scheme, final Project project) {
        final CodeStyleSchemes codeStyleSchemes = CodeStyleSchemes.getInstance();
        final CodeStyleSettingsManager projectManager = CodeStyleSettingsManager.getInstance(project);

        codeStyleSchemes.setCurrentScheme(scheme);
        projectManager.USE_PER_PROJECT_SETTINGS = false;
        projectManager.PREFERRED_PROJECT_CODE_STYLE = scheme.getName();
        projectManager.setMainProjectCodeStyle(scheme.getCodeStyleSettings());
        ApplicationManager.getApplication().invokeLater(projectManager::notifyCodeStyleSettingsChanged);
        CodeStyleSchemesImpl.getSchemeManager().setSchemes(ContainerUtil.filter(codeStyleSchemes.getAllSchemes(), s -> !CodeStyleScheme.PROJECT_SCHEME_NAME.equals(s.getName())), scheme, null);
    }

    @Override
    public void dispose() {
    }
}
