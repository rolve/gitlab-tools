package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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
 * Publishes the content of a given directory into directories of existing
 * repositories (or the root directory). If this command is used repeatedly to
 * publish multiple directories, the 'workDir' option can be used to reduce
 * execution time by avoiding subsequent clone operations.
 */
public class PublishDirectoryCmd extends CmdForProjects<PublishDirectoryCmd.Args> {

    private static final int ATTEMPTS = 3;
    private static final int SLEEP_TIME = 200;
    private static final Set<String> PRIMORDIAL_FILES = Set.of(".git", "README.md");

    public PublishDirectoryCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
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
        for (var project : projects) {
            try {
                var repoDir = workDir.resolve(project.getName());
                if (alreadyPublished(repoDir)) {
                    progress.advance("existing");
                    continue;
                }

                var branch = requireNonNullElse(args.getBranch(), project.getDefaultBranch());

                Git git = null;
                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        if (exists(repoDir)) {
                            git = open(repoDir.toFile());
                            git.fetch()
                                    .setCredentialsProvider(credentials)
                                    .call();

                            var create = git.branchList().call().stream()
                                    .map(Ref::getName)
                                    .noneMatch(("refs/heads/" + branch)::equals);
                            git.checkout()
                                    .setName(branch)
                                    .setCreateBranch(create)
                                    .call();
                            git.merge()
                                    .include(git.getRepository().resolve("origin/" + branch))
                                    .setFastForward(FF)
                                    .call();
                        } else {
                            git = cloneRepository()
                                    .setURI(project.getWebUrl())
                                    .setBranch(branch)
                                    .setDirectory(repoDir.toFile())
                                    .setCredentialsProvider(credentials)
                                    .call();
                            progress.additionalInfo("newly cloned");
                        }
                        break;
                    } catch (TransportException e) {
                        progress.interrupt();
                        e.printStackTrace(System.out);
                        System.out.println(
                                "Transport exception for " + project.getName()
                                        + "! Attempts left: " + attempts);
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
                var message = "Publish " + requireNonNullElse(args.getDestDir(), "directory");
                var commitId = git.commit()
                        .setMessage(message)
                        .call().getId();

                for (var extra : args.getExtraBranches()) {
                    var create = git.branchList().call().stream()
                            .map(Ref::getName)
                            .noneMatch(("refs/heads/" + extra)::equals);
                    git.checkout()
                            .setName(extra)
                            .setCreateBranch(create)
                            .call();
                    // first, merge remote changes
                    git.merge()
                            .include(git.getRepository().resolve("origin/" + extra))
                            .setFastForward(FF)
                            .call();
                    // then merge commit with directory
                    git.merge()
                            .include(commitId)
                            .setMessage(message)
                            .call();
                }

                for (int attempts = ATTEMPTS; attempts-- > 0;) {
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
                                "Transport exception for " + project.getName()
                                        + "! Attempts left: " + attempts);
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
        @Option
        String getDir();

        @Option(defaultToNull = true)
        String getDestDir();

        @Option(defaultToNull = true)
        String getWorkDir();

        /**
         * A GLOB pattern that is applied to the relative path of each file
         * in the directory.
         */
        @Option(defaultToNull = true)
        String getIgnorePattern();

        /**
         * Must already exist in the GitLab repo. If not set, the default
         * branch configured in GitLab is used.
         */
        @Option(defaultToNull = true)
        String getBranch();

        /**
         * A list of extra branches into which the branch with the published
         * directory is merged. Must all exist in the GitLab repo.
         */
        @Option(defaultValue = {})
        List<String> getExtraBranches();
    }
}
