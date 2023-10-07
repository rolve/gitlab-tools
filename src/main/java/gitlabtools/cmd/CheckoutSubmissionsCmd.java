package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.function.Predicate;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.parse;
import static java.time.ZoneId.systemDefault;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

public class CheckoutSubmissionsCmd extends CmdForProjects<CheckoutSubmissionsCmd.Args> {

    private static final int ATTEMPTS = 3;
    private final Instant deadline;

    public CheckoutSubmissionsCmd(String[] rawArgs) throws IOException {
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
    protected void doExecute() throws Exception {
        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Path.of(args.getDestDir());
        createDirectories(destDir);

        var projects = getProjects();
        System.out.println("Checking out " + projects.size() + " projects...");
        for (var project : projects) {
            var repoDir = destDir.resolve(project.getName());
            var branch = requireNonNullElse(args.getBranch(), project.getDefaultBranch());

            var lastPush = getLastPushBefore(project, branch, deadline);
            if (lastPush == null) {
                progress.advance("failed");
                progress.interrupt();
                System.out.printf("Skipping %s, no push events found before deadline.\n",
                        project.getName());
                continue;
            }

            Git git = null;
            for (int attempts = ATTEMPTS; attempts-- > 0; ) {
                try {
                    if (exists(repoDir)) {
                        git = open(repoDir.toFile());
                        // need to switch to default branch, in case we are in
                        // "detached head" state (from previous checkout)
                        git.checkout()
                                .setName(branch)
                                .call();
                        git.pull()
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
                    var lastCommit = lastPush.getPushData().getCommitTo();
                    git.checkout()
                            .setName(lastCommit)
                            .call();
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

            progress.advance();
        }
    }

    interface Args extends CmdForProjects.Args {
        @Option
        String getDestDir();

        /**
         * The deadline for the submissions. If not specified, the current
         * date/time is used. The deadline is interpreted as local time and
         * must be specified in ISO-8601 format, e.g. "2007-12-03T10:15:30".
         */
        @Option(defaultToNull = true)
        String getDeadline();

        @Option(defaultToNull = true)
        String getBranch();
    }
}
