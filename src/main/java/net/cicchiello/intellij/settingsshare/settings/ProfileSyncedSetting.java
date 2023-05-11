package net.cicchiello.intellij.settingsshare.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import lombok.NonNull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ProfileSyncedSetting implements SyncedSetting {

    public static final String DEFAULT_PROFILE_FILE = "Default.xml";

    public static void writeConfigFile(final String data, final String folder, final String filename) throws IOException {
        final String configDir = PathManager.getConfigPath();
        final Path configPath = Path.of(configDir, folder);
        if (!Files.exists(configPath)) {
            Files.createDirectory(configPath);
        }
        final Path fullPath = configPath.resolve(filename);
        try (final OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(fullPath))) {
            writer.write(data);
        }
    }

    @Override
    public List<String> updateProfilesFromPath(final @NonNull Path path, final @NonNull Project project) throws IOException {
        final List<Path> toImport = listProfiles(getProfilePath(path));
        if (toImport.isEmpty()) {
            return List.of();
        }
        final ArrayList<String> updatedProfiles = new ArrayList<>();
        for (final Path profilePath : toImport) {
            updateProfile(profilePath, project)
                    .ifPresent(updatedProfiles::add);
        }
        return updatedProfiles;
    }

    @NonNull
    public String getDefaultProfileFileName() {
        return DEFAULT_PROFILE_FILE;
    }

    @NonNull
    public abstract Path getProfilePath(@NonNull final Path basePath);

    @NonNull
    public abstract Optional<String> updateProfile(@NonNull final Path profileFile, @NonNull final Project project) throws IOException;

    private List<Path> listProfiles(final Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(path)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !getDefaultProfileFileName().equals(p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

}
