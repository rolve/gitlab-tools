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
import java.nio.file.Paths;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

/**
 * Publishes the content in a given "template" directory into a directory of
 * existing repositories (or the root directory). If this command is used
 * repeatedly to publish multiple templates, the 'workDir' option can be
 * used to reduce execution time by avoiding subsequent clone operations.
 */
public class PublishTemplateCmd extends Cmd<PublishTemplateCmd.Args> {

    private static final int ATTEMPTS = 3;
    private static final int SLEEP_TIME = 200;

    public PublishTemplateCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var templateDir = Paths.get(args.getTemplateDir());

        Path workDir;
        if (args.getWorkDir() == null || args.getDestDir() == null) {
            if (args.getWorkDir() != null) {
                System.out.println("Ignoring workDir option when publishing to repository root");
            }
            workDir = createTempDirectory("gitlab-tools");
            workDir.toFile().deleteOnExit();
        } else {
            workDir = Path.of(args.getWorkDir());
            createDirectories(workDir);
        }

        var projects = getProjects(args);
        System.out.println("Publishing template to " + projects.size()
                + " repositories...");
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

                copyDir(templateDir, destDir, args.getIgnorePattern());
                git.add().addFilepattern(".").call();
                var message = "Publish " + requireNonNullElse(args.getDestDir(), "template");
                git.commit().setMessage(message).call();

                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        git.push()
                                .add(branch)
                                .setCredentialsProvider(credentials)
                                .call();
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
                    if (contents.anyMatch(p -> !p.getFileName().toString().equals(".git"))) {
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
                    copy(source, dest.resolve(rel));
                }
            }
        }
    }

    interface Args extends gitlabtools.cmd.Args {
        @Option
        String getTemplateDir();

        @Option(defaultToNull = true)
        String getDestDir();

        @Option(defaultToNull = true)
        String getWorkDir();

        /**
         * A GLOB pattern that is applied to the relative path of each file
         * in the template directory.
         */
        @Option(defaultToNull = true)
        String getIgnorePattern();

        /**
         * Must already exist in the GitLab repo. If not set, the default
         * branch configured in GitLab is used.
         */
        @Option(defaultToNull = true)
        String getBranch();
    }
}
