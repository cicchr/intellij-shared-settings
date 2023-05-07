package net.cicchiello.intellij.settingsshare.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface GitRepoService {

    static GitRepoService getInstance() {
        return ApplicationManager.getApplication().getService(GitRepoService.class);
    }

    List<String> getBranches(@NonNull final Project project, @NonNull final String url) throws IOException;

    Path updateAndCheckoutBranch(@NonNull final Project project, @NonNull final String url, @NonNull final String branch) throws IOException;

    Optional<Path> getRepositoryPath() throws IOException;
}
