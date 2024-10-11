package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.parse;
import static java.time.ZoneId.systemDefault;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

/**
 * Similar to {@link CheckoutCmd}, clones all repositories in the --group into
 * the --destDir directory, but checks out a specific commit that corresponds to
 * the last push before a given deadline (resulting in a detached HEAD state).
 * By using the GitLab event API, this command ensures that the exact same
 * deadline is used for all projects, even if the command is taking a long time
 * to process all of them.
 */
public class CheckoutWithDeadlineCmd extends CmdForProjects<CheckoutWithDeadlineCmd.Args> {

    private static final int ATTEMPTS = 3;

    private final Instant deadline;

    public CheckoutWithDeadlineCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
        try {
            var localDeadline = args.getDeadline() == null
                    ? now()
                    : parse(args.getDeadline());
            deadline = localDeadline.atZone(systemDefault()).toInstant();
            if (args.getDeadline() != null) {
                System.out.println("Using local deadline " + localDeadline + " (UTC: " + deadline + ")");
            }
        } catch (DateTimeParseException e) {
            throw new ArgumentValidationException("Invalid deadline: " + args.getDeadline(), e);
        }
    }

    @Override
    protected void executeTasks() throws Exception {
        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Path.of(args.getDestDir());
        createDirectories(destDir);

        var projects = getProjects();
        System.out.println("Checking out " + projects.size() + " projects...");
        for (var project : projects) {
            var repoDir = destDir.resolve(project.getName());
            var branch = requireNonNullElse(args.getBranch(), project.getDefaultBranch());

            var lastCommit = lastPushedCommitBefore(project, branch, deadline);
            if (lastCommit == null) {
                progress.advance("failed");
                progress.interrupt();
                System.out.printf("Skipping %s, no commits found before deadline.\n",
                        project.getName());
                continue;
            }

            for (int attempts = ATTEMPTS; attempts-- > 0; ) {
                Git git = null;
                try {
                    if (exists(repoDir)) {
                        git = open(repoDir.toFile());
                        if (git.getRepository().getBranch().equals(lastCommit)) {
                            progress.advance("existing");
                            break;
                        }
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

                    // go to last commit before the deadline
                    git.checkout()
                            .setName(lastCommit)
                            .call();

                    progress.advance();
                    break;
                } catch (TransportException e) {
                    progress.interrupt();
                    e.printStackTrace(System.out);
                    System.out.println("Transport exception for " + project.getName() +
                                       "! Attempts left: " + attempts);
                    if (attempts == 0) {
                        throw e;
                    }
                } finally {
                    if (git != null) {
                        git.close();
                    }
                }
            }
        }
    }

    interface Args extends CmdForProjects.Args {
        @Option
        String getDestDir();

        /**
         * The deadline to use to determine the commit to check out. The GitLab
         * API is queried for the last push event before the deadline for the
         * given branch and the corresponding commit is checked out. The
         * deadline is interpreted as local time and must be specified in
         * ISO-8601 format, e.g. "2007-12-03T10:15:30". If unspecified, the time
         * at which the command is started is used.
         */
        @Option(defaultToNull = true)
        String getDeadline();

        /**
         * The branch to use. If unspecified, the default branch of each project
         * is used.
         */
        @Option(defaultToNull = true)
        String getBranch();
    }
}
