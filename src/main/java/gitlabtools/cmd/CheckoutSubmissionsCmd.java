package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.models.Event;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.util.Calendar.DAY_OF_MONTH;
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
        var deadline = args.getDate() == null ? null :
                new SimpleDateFormat("yyyy-MM-dd-HH:mm").parse(args.getDate());
        if (deadline != null) {
            System.out.println(deadline);
        }

        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var workDir = Paths.get(args.getWorkDir());
        createDirectories(workDir);

        var projects = getProjects(args);
        System.out.println("Checking out " + projects.size() + " projects...");
        for (var project : projects) {
            var repoDir = workDir.resolve(project.getName());

            Event lastPush = null;
            if (deadline != null) {
                // add 1 day to deadline since gitlab ignores time of day
                var tempCal = Calendar.getInstance();
                tempCal.setTime(deadline);
                tempCal.add(DAY_OF_MONTH, 1);

                // fetch all push-events the day of the deadline (and before)
                var pager = gitlab.getEventsApi().getProjectEvents(project.getId(),
                        PUSHED, null, tempCal.getTime(), null, DESC, 100);

                lastPush = stream(pager)
                        .filter(e -> e.getCreatedAt().before(deadline))
                        .filter(e -> e.getPushData().getRef().equals("master"))
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
                        // need to switch to master, in case we are in "detached head"
                        // state (from previous checkout)
                        git.checkout()
                                .setName("master")
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
        String getWorkDir();

        @Option(defaultToNull = true)
        String getDate(); // yyyy-MM-dd-HH:mm
    }
}
