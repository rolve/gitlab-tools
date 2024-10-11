package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;
import static org.eclipse.jgit.api.MergeCommand.FastForwardMode.FF;

/**
 * Publishes the content of a given directory into all repositories in the given
 * group. To do this, each repository is cloned into a local working directory,
 * the given directory is copied into it and the changes are committed and
 * pushed back to the server. By default, the working directory is deleted
 * afterward; if this command is to be used repeatedly to publish multiple
 * directories, a persistent working directory can be defined using the
 * 'workDir' option, reducing execution time of subsequent publish operations by
 * avoiding repeated cloning.
 * <p>
 * If a non-empty directory with the same path already exists in the repository
 * (in the given branch), the command assumes that the directory has been
 * published before and skips the repository.
 */
public class PublishDirectoryCmd extends CmdForProjects<PublishDirectoryCmd.Args> {

    private static final int ATTEMPTS = 3;
    private static final int SLEEP_TIME = 200;
    private static final Set<String> PRIMORDIAL_FILES = Set.of(".git", "README.md");

    public PublishDirectoryCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void executeTasks() throws Exception {
        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var dir = Path.of(args.getDir());

        Path workDir;
        if (args.getWorkDir() == null) {
            workDir = createTempDirectory("gitlab-tools");
            workDir.toFile().deleteOnExit();
        } else {
            workDir = Path.of(args.getWorkDir());
            createDirectories(workDir);
        }

        var projects = getProjects();
        System.out.println("Publishing directory to " + projects.size() + " repositories...");
        projects: for (var project : projects) {
            try {
                var repoDir = workDir.resolve(project.getName());
                if (alreadyPublished(repoDir)) {
                    progress.advance("existing");
                    continue;
                }

                var branch = requireNonNullElse(args.getBranch(), project.getDefaultBranch());

                Git git = null;
                for (int attempts = ATTEMPTS; attempts-- > 0; ) {
                    try {
                        if (exists(repoDir)) {
                            git = open(repoDir.toFile());
                            git.fetch()
                                    .setCredentialsProvider(credentials)
                                    .call();
                        } else {
                            git = cloneRepository()
                                    .setURI(project.getWebUrl())
                                    .setDirectory(repoDir.toFile())
                                    .setCredentialsProvider(credentials)
                                    .call();
                            progress.additionalInfo("newly cloned");
                        }

                        var allBranches = new ArrayList<>(List.of(branch));
                        allBranches.addAll(args.getExtraBranches());
                        for (var b : allBranches) {
                            var remote = git.getRepository().findRef("origin/" + b);
                            if (remote == null) {
                                progress.advance("failed");
                                progress.interrupt();
                                System.out.println("Remote branch " + b + " not found for " + project.getName());
                                continue projects;
                            }
                        }

                        checkOutRemoteBranch(git, branch);
                        break;
                    } catch (TransportException e) {
                        progress.interrupt();
                        e.printStackTrace(System.out);
                        System.out.println(
                                "Transport exception for " + project.getName() +
                                "! Attempts left: " + attempts);
                        if (attempts == 0) {
                            throw e;
                        }
                    }
                }
                if (alreadyPublished(repoDir)) {
                    progress.advance("existing");
                    continue;
                }

                Path destDir;
                if (args.getDestDir() == null) {
                    destDir = repoDir;
                } else {
                    destDir = repoDir.resolve(args.getDestDir());
                    createDirectories(destDir);
                }

                copyDir(dir, destDir, args.getIgnorePattern());
                git.add().addFilepattern(".").call();
                var message = requireNonNullElse(args.getCommitMessage(),
                        "Publish " + requireNonNullElse(args.getDestDir(), "directory"));
                var commitId = git.commit()
                        .setMessage(message)
                        .call().getId();

                for (var extra : args.getExtraBranches()) {
                    checkOutRemoteBranch(git, extra);
                    git.merge()
                            .include(commitId)
                            .setMessage(message)
                            .call();
                }

                for (int attempts = ATTEMPTS; attempts-- > 0; ) {
                    try {
                        var push = git.push()
                                .add(branch)
                                .setCredentialsProvider(credentials);
                        for (var extra : args.getExtraBranches()) {
                            push.add(extra);
                        }
                        push.call();
                        break;
                    } catch (TransportException e) {
                        progress.interrupt();
                        e.printStackTrace(System.out);
                        System.out.println(
                                "Transport exception for " + project.getName() +
                                "! Attempts left: " + attempts);
                        if (attempts == 0) {
                            throw e;
                        }
                    }
                }

                git.close();
                progress.advance();

                Thread.sleep(SLEEP_TIME);
            } catch (Exception e) {
                progress.advance("failed");
                progress.interrupt();
                System.out.println("Problem with " + project.getName() + ":");
                e.printStackTrace(System.out);
            }
        }
    }

    /**
     * Helper method to check out the remote branch with the given name. If
     * there already exists a local branch with that name, it is checked out and
     * fast-forwarded to the remote branch, if necessary. Otherwise, it is
     * created with the remote branch as the start point. In both cases, the
     * remote branch must already exist, i.e., any fetching must be done before
     * calling this method.
     */
    private static void checkOutRemoteBranch(Git git, String branch) throws GitAPIException, IOException {
        // apparently, there is no cleaner way to do this...
        var create = git.branchList().call().stream()
                .map(Ref::getName)
                .noneMatch(("refs/heads/" + branch)::equals);
        if (create) {
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setStartPoint("origin/" + branch)
                    .call();
        } else {
            git.checkout()
                    .setName(branch)
                    .call();
            git.merge()
                    .include(git.getRepository().findRef("origin/" + branch))
                    .setFastForward(FF)
                    .call();
        }
    }

    private boolean alreadyPublished(Path repoDir) throws IOException {
        if (args.getDestDir() == null) {
            if (exists(repoDir)) {
                try (var contents = list(repoDir)) {
                    if (contents.anyMatch(p -> !PRIMORDIAL_FILES.contains(p.getFileName().toString()))) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            return exists(repoDir.resolve(args.getDestDir()));
        }
    }

    private static void copyDir(Path src, Path dest, String ignorePattern) throws IOException {
        PathMatcher matcher = null;
        if (ignorePattern != null) {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + ignorePattern);
        }
        try (var walk = walk(src).skip(1)) {
            for (var source : (Iterable<Path>) walk::iterator) {
                var rel = src.relativize(source);
                if (matcher == null || !matcher.matches(rel)) {
                    copy(source, dest.resolve(rel), REPLACE_EXISTING);
                }
            }
        }
    }

    interface Args extends CmdForProjects.Args {
        /**
         * The local file system path to the directory to publish.
         */
        @Option
        String getDir();

        /**
         * The path of a directory in the GitLab repository into which the
         * content of the local directory is published, e.g., "foo/bar". If
         * unspecified, the content is published to the root of the repository.
         */
        @Option(defaultToNull = true)
        String getDestDir();

        /**
         * The local file system path to a directory that is used to clone the
         * repositories. This directory is created if it does not exist. If
         * unspecified, a temporary directory is used and deleted afterward.
         * Otherwise, the working directory is kept to speed up subsequent
         * publish operations.
         */
        @Option(defaultToNull = true)
        String getWorkDir();

        /**
         * A GLOB pattern that is applied to the relative path of each file in
         * the directory. Matching files are not published. If unspecified, all
         * files are published.
         */
        @Option(defaultToNull = true)
        String getIgnorePattern();

        /**
         * The commit message to use for the commit that publishes the
         * directory. If unspecified, a default message is used.
         */
        @Option(defaultToNull = true)
        String getCommitMessage();

        /**
         * The branch to publish the directory into. Must already exist in the
         * GitLab repository. If unspecified, the default branch configured in
         * GitLab is used.
         */
        @Option(defaultToNull = true)
        String getBranch();

        /**
         * A list of extra branches into which the branch with the published
         * directory is merged. Must all exist in the GitLab repository.
         */
        @Option(defaultValue = {})
        List<String> getExtraBranches();
    }
}
