package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.models.Project;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static gitlabtools.CourseFileReader.readSimpleCourseFile;
import static java.nio.file.Files.*;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;
import static org.eclipse.jgit.api.MergeCommand.FastForwardMode.FF;

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
        if (args.getCourseFile() != null) {
            projects = filterProjects(projects);
        }
        System.out.println("Publishing template to " + projects.size() + " repositories...");
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

                copyDir(templateDir, destDir, args.getIgnorePattern());
                git.add().addFilepattern(".").call();
                var message = "Publish " + requireNonNullElse(args.getDestDir(), "template");
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
                    // then merge commit with template
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

    private List<Project> filterProjects(List<Project> projects) throws IOException {
        var names = Set.copyOf(readSimpleCourseFile(Path.of(args.getCourseFile())));
        Predicate<Project> filter = args.isWithProjectNamePrefix()
                ? p -> names.contains(p.getName().split("_", 2)[1])
                : p -> names.contains(p.getName());
        return projects.stream()
                .filter(filter)
                .collect(toList());
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

        /**
         * A list of extra branches into which the branch with the published
         * template is merged. Must all exist in the GitLab repo.
         */
        @Option(defaultValue = {})
        List<String> getExtraBranches();

        /**
         * When specified, the template will be published only to the
         * projects belonging to the students in the file. Note that
         * is the projects have been created with a project name prefix,
         * the {@link #isWithProjectNamePrefix()} option is required
         * for this to work.
         */
        @Option(defaultToNull = true)
        String getCourseFile();

        @Option
        boolean isWithProjectNamePrefix();
    }
}
