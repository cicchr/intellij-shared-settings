package net.cicchiello.intellij.settingsshare.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import lombok.NonNull;
import org.apache.commons.io.file.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GitRepoServiceImpl implements GitRepoService, Disposable {

    private static final Logger log = Logger.getInstance(GitRepoServiceImpl.class);
    public static final String GIT_DEFAULT_REMOTE = "origin";
    public static final String REPO_FOLDER = "repo";
    public static final String TEST_REPO_FOLDER = "test";

    private final Lock lock = new ReentrantLock(true);

    @Override
    public List<String> getBranches(@NonNull final Project project, @NonNull final String url) throws IOException {
        lock.lock();
        try {
            final VirtualFile repo = getRepository(project, url, TEST_REPO_FOLDER, false);
            if (repo == null) {
                return List.of();
            }
            return listRepoBranches(repo, project);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Path updateAndCheckoutBranch(@NonNull final Project project, @NonNull final String url, @NonNull final String branch) throws IOException {
        lock.lock();
        try {
            final VirtualFile repo = getRepository(project, url, REPO_FOLDER, false);
            if (repo == null) {
                throw new IOException(String.format("Unable to find repository %s. Please check your settings", url));
            }
            checkoutAndResetBranch(repo, project, branch);
            return Path.of(repo.getPath());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Path> getRepositoryPath() throws IOException {
        final Path path = getRepoLocation(REPO_FOLDER);
        final VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        if (file == null) {
            throw new IOException("Failed to create directory to checkout repo");
        }
        // Sometimes the VirtualFile can get out of sync so refresh it just in case
        file.refresh(false, false);
        if (GitUtil.findGitDir(file) == null) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private VirtualFile getRepository(final Project project, final String repoUrl, final String repoFolder, final boolean retry) throws IOException {
        final Path path = getRepoLocation(repoFolder);
        final VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        if (file == null) {
            throw new IOException("Failed to create directory to checkout repo");
        }
        // Sometimes the VirtualFile can get out of sync so refresh it just in case
        file.refresh(false, false);
        boolean cloned = false;
        if (GitUtil.findGitDir(file) == null && !(cloned = cloneRepository(project, file, repoUrl))) {
            throw new IOException(String.format("Failed to clone %s", repoUrl));
        }
        final Git git = Git.getInstance();
        final GitLineHandler remoteGetUrl = new GitLineHandler(project, file, GitCommand.REMOTE);
        remoteGetUrl.addParameters("get-url");
        remoteGetUrl.addParameters(GIT_DEFAULT_REMOTE);
        final GitCommandResult remoteResult = git.runCommand(remoteGetUrl);
        if (!remoteResult.success()) {
            throw new IOException("Failed to check remote: " + remoteResult.getErrorOutputAsHtmlString());
        }
        final Optional<String> remote = remoteResult.getOutput()
                .stream()
                .findFirst();
        if (remote.isEmpty() || !remote.get().equals(repoUrl)) {
            if (retry) {
                throw new IOException(String.format("Failed to get local repository: %s", file));
            }
            log.info(String.format("Git repository origin %s does not match wanted origin %s", remote.orElse(null), repoUrl));
            deleteRepository(repoFolder);
            return getRepository(project, repoUrl, repoFolder, true);
        }

        if (!cloned && !fetchRepository(file, project)) {
            log.warn("Failed to fetch repository");
            return null;
        }
        return file;
    }

    private Path getRepoLocation(final String repo) throws IOException {
        // We'll store this repo in IntelliJ's configuration since this should be shared across all projects
        final String configPath = PathManager.getConfigPath();
        final Path repoFolder = Path.of(configPath, "sharedSettings").resolve(repo);
        if (Files.notExists(repoFolder)) {
            log.info(String.format("Creating shared settings repository folder in %s", repoFolder));
            Files.createDirectories(repoFolder);
        }
        return repoFolder;
    }

    private boolean fetchRepository(final VirtualFile repo, final Project project) throws IOException {
        log.info(String.format("Fetching repository %s", repo.getPath()));
        final GitLineHandler fetchCmd = new GitLineHandler(project, repo, GitCommand.FETCH);
        final GitCommandResult result = Git.getInstance().runCommand(fetchCmd);
        if (!result.success()) {
            throw new IOException(String.format("Failed to fetch repo: %s", result.getErrorOutputAsHtmlString()));
        }
        return true;
    }

    private void checkoutAndResetBranch(final VirtualFile repo, final Project project, final String branchName) throws IOException {
        final Git git = Git.getInstance();
        // Get the commit hash of the newest commit on the configured branch of the remote
        final GitLineHandler hashCmd = new GitLineHandler(project, repo, GitCommand.REV_PARSE);
        hashCmd.addParameters(branchName);
        final Optional<String> maybeHash = Optional.of(git.runCommand(hashCmd))
                .filter(GitCommandResult::success)
                .map(GitCommandResult::getOutput)
                .filter(output -> !output.isEmpty())
                .map(output -> output.get(0))
                .filter(hash -> !hash.isBlank());

        if (maybeHash.isEmpty()) {
            throw new IOException(String.format("Failed to checkout branch %s because the remote does not have any commits", branchName));
        }
        final String hash = maybeHash.get();
        // Checkout the commit hash. We don't need to actually be on a branch here, and it was easier to just checkout the commit directly
        final GitLineHandler h = new GitLineHandler(project, repo, GitCommand.CHECKOUT);
        h.addParameters("--force");
        h.addParameters(String.format("%s^0", hash));
        h.endOptions();
        final GitCommandResult result = git.runCommand(h);
        if (!result.success()) {
            throw new IOException(String.format("Failed to checkout branch %s: %s", branchName, result.getErrorOutputAsJoinedString()));
        }
        try {
            // Cleanup untracked files. This shouldn't really be needed unless someone was messing around in IntelliJ's config
            for (final FilePath path : git.untrackedFilePaths(project, repo, null)) {
                final Path file = Path.of(path.getPath());
                if (!Files.exists(file)) {
                    continue;
                }
                PathUtils.delete(file);
            }
        } catch (final VcsException e) {
            throw new IOException(String.format("Failed to cleanup untracked files in %s: %s", repo.getPath(), e.getMessage()), e);
        }
    }

    private List<String> listRepoBranches(final VirtualFile repo, final Project project) throws IOException {
        final GitLineHandler branchCmd = new GitLineHandler(project, repo, GitCommand.BRANCH);
        branchCmd.addParameters("-r");
        final GitCommandResult branchResult = Git.getInstance().runCommand(branchCmd);
        if (!branchResult.success()) {
            throw new IOException("Failed to list branches: " + branchResult.getErrorOutputAsHtmlString());
        }
        return branchResult.getOutput().stream()
                .map(String::trim)
                .filter(s -> !s.contains(" ")) // This will filter out remote HEAD branches which we don't want
                .toList();
    }

    private boolean cloneRepository(final Project project, final VirtualFile file, final String url) {
        log.info(String.format("Cloning repository from %s to %s", url, file.getPath()));
        return GitCheckoutProvider.doClone(project, Git.getInstance(), file.getName(), file.getParent().getPath(), url);
    }

    private void deleteRepository(final String repoFolder) throws IOException {
        final Path path = getRepoLocation(repoFolder);
        if (Files.exists(path)) {
            log.info(String.format("Deleting repository at %s", path));
            PathUtils.deleteDirectory(path);
        }
    }

    @Override
    public void dispose() {
    }

}
