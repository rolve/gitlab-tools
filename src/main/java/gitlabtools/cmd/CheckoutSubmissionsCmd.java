package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.models.Event;

import java.nio.file.Paths;
import java.util.Date;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.time.LocalDateTime.parse;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;
import static org.gitlab4j.api.Constants.ActionType.PUSHED;
import static org.gitlab4j.api.Constants.SortOrder.DESC;

public class CheckoutSubmissionsCmd extends Cmd<CheckoutSubmissionsCmd.Args> {

    private static final int ATTEMPTS = 3;

    public CheckoutSubmissionsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var localDeadline = args.getDate() == null ? null
                : parse(args.getDate());
        var deadline = localDeadline == null ? null
                : localDeadline.atZone(systemDefault()).toInstant();
        if (deadline != null) {
            System.out.println("Using local deadline " + localDeadline + " (UTC: " + deadline + ")");
        }

        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var workDir = Paths.get(args.getDir());
        createDirectories(workDir);

        var projects = getProjects(args);
        System.out.println("Checking out " + projects.size() + " projects...");
        for (var project : projects) {
            var repoDir = workDir.resolve(project.getName());

            Event lastPush = null;
            if (deadline != null) {
                // fetch all push-events on the day of the deadline and earlier
                // (actually, add 1 day to deadline since GitLab ignores time of day)
                var pager = gitlab.getEventsApi().getProjectEvents(project.getId(),
                        PUSHED, null, Date.from(deadline.plus(1, DAYS)), null, DESC, 100);

                // filter precisely here:
                lastPush = stream(pager)
                        .filter(e -> e.getPushData().getRef().equals(args.getDefaultBranch()))
                        .filter(e -> e.getCreatedAt().before(Date.from(deadline)))
                        .findFirst().orElse(null);

                if (lastPush == null) {
                    progress.advance("failed");
                    progress.interrupt();
                    System.out.printf("Skipping %s, no push events found before date.\n", project.getName());
                    continue;
                }
            }

            Git git = null;
            for (int attempts = ATTEMPTS; attempts-- > 0;) {
                try {
                    if (exists(repoDir)) {
                        git = open(repoDir.toFile());
                        // need to switch to default branch, in case we are in "detached head"
                        // state (from previous checkout)
                        git.checkout()
                                .setName(args.getDefaultBranch())
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

                    if (deadline != null) {
                        // go to last commit before the deadline
                        String lastCommitSHA = lastPush.getPushData().getCommitTo();

                        git.checkout()
                                .setName(lastCommitSHA)
                                .call();
                    }
                    // done
                    attempts = 0;
                } catch (TransportException e) {
                    progress.interrupt();
                    e.printStackTrace(System.out);
                    System.out.println("Transport exception for " + project.getName() +
                            "! Attempts left: " + attempts);
                    if (attempts == 0) {
                        throw e;
                    }
                } finally {
                    if (git != null)
                        git.close();
                }
            }
            progress.advance();
        }
    }

    interface Args extends gitlabtools.cmd.Args {
        @Option
        String getDir();

        @Option(defaultToNull = true)
        String getDate();
    }
}
