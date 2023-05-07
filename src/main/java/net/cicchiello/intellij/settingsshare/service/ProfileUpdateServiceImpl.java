package net.cicchiello.intellij.settingsshare.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.action.SettingChangedListener;
import net.cicchiello.intellij.settingsshare.settings.SyncedSetting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;

public class ProfileUpdateServiceImpl implements ProfileUpdateService, Disposable {

    public static final String ENFORCED_PROPERTIES_FILE = "enforced.properties";
    private static final ServiceLoader<SyncedSetting> settingsServiceLoader = ServiceLoader.load(SyncedSetting.class, SyncedSetting.class.getClassLoader());
    private final List<SyncedSetting> settings;

    public ProfileUpdateServiceImpl() {
        settings = settingsServiceLoader.stream()
                .map(ServiceLoader.Provider::get)
                .peek(setting -> Disposer.register(this, setting))
                .toList();
    }

    @Override
    public Multimap<SyncedSetting, String> importGlobalProfilesFromPath(@NonNull final Path path, @NonNull final Project project) throws IOException {
        final Multimap<SyncedSetting, String> result = ArrayListMultimap.create();
        for (final SyncedSetting setting : settings) {
            result.putAll(setting, setting.updateProfilesFromPath(path, project));
        }
        return result;
    }

    @Override
    public Map<SyncedSetting, String> enforceSettings(@NonNull final Path path, @NonNull final Project project) throws IOException {
        final Properties enforcedProfiles = readSettings(path);
        final Map<SyncedSetting, String> enforcedSettings = new HashMap<>();
        for (final SyncedSetting setting : settings) {
            if (enforcedProfiles.containsKey(setting.getConfigName())) {
                setting.enforceSetting(enforcedProfiles.getProperty(setting.getConfigName()), project)
                        .ifPresent(note -> enforcedSettings.put(setting, note));
            }
        }
        return enforcedSettings;
    }

    @Override
    public Optional<String> enforceSetting(@NonNull final SyncedSetting setting, @NonNull final Project project, @NonNull final Path repoPath) throws IOException {
        final Properties enforcedProfiles = readSettings(repoPath);
        if (enforcedProfiles.containsKey(setting.getConfigName())) {
            return setting.enforceSetting(enforcedProfiles.getProperty(setting.getConfigName()), project);
        }
        return Optional.empty();
    }

    @Override
    public void addEnforcementListeners(final @NonNull Project project, final @NonNull SettingChangedListener listener) {
        settings.forEach(setting -> setting.addSettingChangedListener(project, listener));
    }

    private Properties readSettings(final Path path) throws IOException {
        final Properties enforcedProfiles = new Properties();
        final Path enforcedPath = path.resolve(ENFORCED_PROPERTIES_FILE);
        if (!Files.isRegularFile(enforcedPath)) {
            return enforcedProfiles;
        }
        try (final InputStream is = Files.newInputStream(enforcedPath)) {
            enforcedProfiles.load(is);
        }
        return enforcedProfiles;
    }

    @Override
    public void dispose() {
    }
}
