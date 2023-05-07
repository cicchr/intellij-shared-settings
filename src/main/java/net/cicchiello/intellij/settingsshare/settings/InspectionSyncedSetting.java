package net.cicchiello.intellij.settingsshare.settings;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.ApplicationInspectionProfileManager;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import lombok.NonNull;
import net.cicchiello.intellij.settingsshare.action.SettingChangedListener;
import org.apache.commons.compress.utils.FileNameUtils;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class InspectionSyncedSetting extends ProfileSyncedSetting implements Disposable {

    public static final String INSPECTION_PROFILE_FOLDER = "inspection";
    public static final String INSPECTION_SETTING_NAME = "Inspection";

    @NonNull
    @Override
    public String getHumanName() {
        return INSPECTION_SETTING_NAME;
    }

    @NonNull
    @Override
    public String getConfigName() {
        return INSPECTION_PROFILE_FOLDER;
    }

    @NonNull
    @Override
    public Optional<String> enforceSetting(@NonNull final String profileName, @NonNull final Project project) {
        final ApplicationInspectionProfileManager appManager = ApplicationInspectionProfileManager.getInstanceImpl();
        final ProjectInspectionProfileManager projectManager = ProjectInspectionProfileManager.getInstance(project);
        boolean enforced = false;
        if (!appManager.getCurrentProfile().getName().equals(profileName) && appManager.getProfile(profileName, false) != null) {
            appManager.setRootProfile(profileName);
            enforced = true;
        }
        if (projectManager.getCurrentProfile().getProfileManager() instanceof ProjectInspectionProfileManager || !profileName.equals(projectManager.getCurrentProfile().getName())) {
            projectManager.useApplicationProfile(profileName);
            enforced = true;
        }
        return enforced ? Optional.of(profileName) : Optional.empty();
    }

    @Override
    public void addSettingChangedListener(final @NonNull Project project, final @NonNull SettingChangedListener listener) {
        // TODO: Unfortunately this does not fire when the profile is changed to a project profile. I can't seem to figure out how to fix that
        project.getMessageBus().connect(this).subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
            @Override
            public void profileChanged(@NotNull final InspectionProfile profile) {
                listener.settingChanged(InspectionSyncedSetting.this, project);
            }

            @Override
            public void profileActivated(@Nullable final InspectionProfile oldProfile, @Nullable final InspectionProfile profile) {
                listener.settingChanged(InspectionSyncedSetting.this, project);
            }
        });
    }

    @NonNull
    @Override
    public Path getProfilePath(@NonNull final Path basePath) {
        return basePath.resolve(INSPECTION_PROFILE_FOLDER);
    }

    @NonNull
    @Override
    public Optional<String> updateProfile(@NonNull final Path path, @NonNull final Project project) throws IOException {
        final ApplicationInspectionProfileManager profileManager = ApplicationInspectionProfileManager.getInstanceImpl();
        try {
            final String profileName = FileNameUtils.getBaseName(path.getFileName().toString());
            final InspectionProfileImpl newProfile = profileManager.loadProfile(path.toString());
            if (newProfile == null) {
                throw new IOException(String.format("Failed to load inspection profile %s: loadProfile returned null", path.getFileName()));
            }
            newProfile.setName(profileName);

            final Optional<InspectionProfileImpl> existingProfile = Optional.ofNullable(profileManager.getProfile(profileName, false));
            final String newDom = JDOMUtil.write(newProfile.writeScheme());
            if (existingProfile.isEmpty() || !newDom.equals(JDOMUtil.write(existingProfile.get().writeScheme()))) {
                existingProfile.ifPresent(profileManager::deleteProfile);
                profileManager.addProfile(newProfile);
                writeConfigFile(newDom, INSPECTION_PROFILE_FOLDER, path.getFileName().toString());
                return Optional.of(profileName);
            }
        } catch (final JDOMException e) {
            throw new IOException(String.format("Failed to load inspection profile %s: %s", path.getFileName(), e.getMessage()), e);
        }
        return Optional.empty();
    }

    @Override
    public void dispose() {
    }
}
