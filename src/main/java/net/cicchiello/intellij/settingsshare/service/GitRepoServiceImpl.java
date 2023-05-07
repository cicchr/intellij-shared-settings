package net.cicchiello.intellij.settingsshare.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryImpl;
import lombok.NonNull;
import org.apache.commons.io.file.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GitRepoServiceImpl implements GitRepoService, Disposable {

    private static final Logger log = Logger.getInstance(GitRepoServiceImpl.class);
    public static final String GIT_DEFAULT_REMOTE = "origin";
    public static final String REPO_FOLDER = "repo";
    public static final String TEST_REPO_FOLDER = "test";

    private final Lock lock = new ReentrantLock(true);

    @Override
    public List<String> getBranches(@NonNull final Project project, @NonNull final String url) throws IOException {
        lock.lock();
        final GitRepository repo = getRepository(project, url, TEST_REPO_FOLDER);
        if (repo == null) {
            return List.of();
        }
        try {
            return repo.getBranches()
                    .getRemoteBranches()
                    .stream()
                    .map(GitRemoteBranch::getNameForRemoteOperations)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
            Disposer.dispose(repo);
        }
    }

    @Override
    public Path updateAndCheckoutBranch(@NonNull final Project project, @NonNull final String url, @NonNull final String branch) throws IOException {
        lock.lock();
        final GitRepository repo = getRepository(project, url, REPO_FOLDER);
        if (repo == null) {
            throw new IOException(String.format("Unable to find repository %s. Please check your settings", url));
        }
        try {
            checkoutAndResetBranch(repo, branch);
            return Path.of(repo.getRoot().getPath());
        } finally {
            lock.unlock();
            Disposer.dispose(repo);
        }
    }

    @Override
    public Optional<Path> getRepositoryPath() throws IOException {
        final Path path = getRepoLocation(REPO_FOLDER);
        final VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        if (file == null) {
            throw new IOException("Failed to create directory to checkout repo");
        }
        // Sometimes the VirtualFile can get out of sync since we are bypassing IntelliJ's API in some cases. This is because we need to checkout the git repo
        //  in a non-standard way
        file.refresh(false, false);
        if (GitUtil.findGitDir(file) == null) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private GitRepository getRepository(final Project project, final String repoUrl, final String repoFolder) throws IOException {
        final Path path = getRepoLocation(repoFolder);
        final VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
        if (file == null) {
            throw new IOException("Failed to create directory to checkout repo");
        }
        // Sometimes the VirtualFile can get out of sync since we are bypassing IntelliJ's API in some cases. This is because we need to checkout the git repo
        //  in a non-standard way
        file.refresh(false, false);
        boolean cloned = false;
        if (GitUtil.findGitDir(file) == null && !(cloned = cloneRepository(project, file, repoUrl))) {
            throw new IOException(String.format("Failed to clone %s", repoUrl));
        }
        // This is an internal API, but there's no other way to get a repo without adding it to the VCS mappings of the project which I don't want to do
        //  I also don't want to manually call git commands, so I'm going to use this internal API and I'll change it later of this breaks
        final GitRepository gitRepo = GitRepositoryImpl.createInstance(file, project, this);
        boolean disposeRepo = true;
        try {
            final Optional<String> originRemote = gitRepo.getRemotes().stream()
                    .filter(r -> GIT_DEFAULT_REMOTE.equals(r.getName()))
                    .map(GitRemote::getFirstUrl)
                    .filter(Objects::nonNull)
                    .findFirst();
            // If the remote does not match the configured remote, delete the repo and get a new one
            if (originRemote.isEmpty() || !originRemote.get().equals(repoUrl)) {
                log.info(String.format("Git repository origin %s does not match wanted origin %s", originRemote.orElse(null), repoUrl));
                Disposer.dispose(gitRepo);
                disposeRepo = false;
                deleteRepository(repoFolder);
                return getRepository(project, repoUrl, repoFolder);
            }
            // If we didn't just clone the repo, lets fetch it, so it is up-to-date
            if (!cloned && !fetchRepository(gitRepo)) {
                log.warn("Failed to fetch repository");
                return null;
            }
            disposeRepo = false;
        } finally {
            if (disposeRepo) {
                Disposer.dispose(gitRepo);
            }
        }
        return gitRepo;
    }

    private Path getRepoLocation(final String repo) throws IOException {
        // We'll store this repo in IntelliJ's configuration since this should be shared across all projects
        final String configPath = System.getProperty("idea.config.path");
        final Path repoFolder = Path.of(configPath, "sharedSettings").resolve(repo);
        if (Files.notExists(repoFolder)) {
            log.info(String.format("Creating shared settings repository folder in %s", repoFolder));
            Files.createDirectories(repoFolder);
        }
        return repoFolder;
    }

    private boolean fetchRepository(final GitRepository repo) {
        log.info(String.format("Fetching repository %s", repo.getRoot().getPath()));
        final GitFetchSupport fetchSupport = repo.getProject().getService(GitFetchSupport.class);
        // Fetch the remote repository, so we can get the know if there are updated commits
        return repo.getRemotes()
                .stream()
                .filter(r -> GIT_DEFAULT_REMOTE.equals(r.getName()))
                .findFirst()
                .map(remote -> fetchSupport.fetch(repo, remote).showNotificationIfFailed())
                .orElse(false);
    }

    private void checkoutAndResetBranch(final GitRepository repo, final String branchName) throws IOException {
        final Git git = Git.getInstance();
        // Get the commit hash of the newest commit on the configured branch of the remote
        final Optional<Hash> maybeHash = repo.getBranches().getRemoteBranches()
                .stream()
                .filter(b -> branchName.equals(b.getNameForRemoteOperations()))
                .findAny()
                .map(b -> repo.getBranches().getHash(b));
        if (maybeHash.isEmpty()) {
            throw new IOException(String.format("Failed to checkout branch %s because the remote does not have any commits", branchName));
        }
        // Checkout the commit hash. We don't need to actually be on a branch here, and it was easier to just checkout the commit directly
        final GitCommandResult result = git.checkout(repo, maybeHash.get().asString(), null, true, true, true);
        if (!result.success()) {
            throw new IOException(String.format("Failed to checkout branch %s: %s", branchName, result.getErrorOutputAsJoinedString()));
        }
        try {
            // Cleanup untracked files. This shouldn't really be needed unless someone was messing around in IntelliJ's config
            for (final FilePath path : git.untrackedFilePaths(repo.getProject(), repo.getRoot(), null)) {
                final Path file = Path.of(path.getPath());
                if (!Files.exists(file)) {
                    continue;
                }
                PathUtils.delete(file);
            }
        } catch (final VcsException e) {
            throw new IOException(String.format("Failed to cleanup untracked files in %s: %s", repo.getRoot().getPath(), e.getMessage()), e);
        }
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
