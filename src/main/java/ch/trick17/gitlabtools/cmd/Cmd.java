package ch.trick17.gitlabtools.cmd;

import ch.trick17.gitlabtools.auth.AuthenticationException;
import ch.trick17.gitlabtools.auth.TokenCreationException;
import ch.trick17.gitlabtools.auth.TokenCreator;
import ch.trick17.gitlabtools.ProgressTracker;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Event;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.PushData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Predicate;

import static java.nio.file.Files.readAllLines;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.gitlab4j.api.Constants.ActionType.PUSHED;
import static org.gitlab4j.api.Constants.SortOrder.DESC;

public abstract class Cmd<A extends Args> {

    public interface Constructor {
        Cmd<?> construct(String[] args) throws IOException;
    }

    protected final A args;
    protected final String token;
    protected final GitLabApi gitlab;

    protected ProgressTracker progress;

    public Cmd(A args) throws IOException {
        this.args = args;

        var tokenFile = Path.of(args.getTokenFile());
        if (Files.notExists(tokenFile)) {
            promptCreateToken();
        }
        token = readAllLines(tokenFile).get(0);
        gitlab = new GitLabApi(args.getGitLabUrl(), token);
    }

    private void promptCreateToken() throws IOException {
        var shortUrl = args.getGitLabUrl()
                .replaceAll("^https?://", "")
                .replaceAll("/$", "");
        System.out.print("Token file '" + args.getTokenFile() + "' does not exist. " +
                "Create a new access token on " + shortUrl + "? [Y/n] ");
        var reply = new Scanner(System.in).nextLine().strip().toLowerCase();
        if (reply.isEmpty() || reply.charAt(0) != 'n') {
            createToken();
        } else {
            System.exit(0);
        }
    }

    private void createToken() throws IOException {
        var creator = new TokenCreator(args.getGitLabUrl());
        var scanner = new Scanner(System.in);
        while (true) {
            System.out.print("GitLab username? ");
            var username = scanner.nextLine();
            System.out.print("GitLab password? ");
            String password;
            if (System.console() != null) {
                password = new String(System.console().readPassword()); // no echo
            } else {
                password = scanner.nextLine();
            }
            try {
                var token = creator.createAccessToken(username, password, "gitlab-tools");
                var tokenFile = Path.of(args.getTokenFile());
                Files.writeString(tokenFile, token);
                System.out.println("Access token stored in " + tokenFile.toAbsolutePath());
                break;
            } catch (AuthenticationException e) {
                System.out.print("Authentication failed, likely due to invalid credentials. Retry? [Y/n] ");
                var reply = scanner.nextLine().strip().toLowerCase();
                if (!reply.isEmpty() && reply.charAt(0) == 'n') {
                    System.exit(0);
                }
                // else: try again
            } catch (TokenCreationException e) {
                var slash = args.getGitLabUrl().endsWith("/") ? "" : "/";
                System.out.println("\nCould not create token. Create the token manually here:\n" +
                        args.getGitLabUrl() + slash + "-/profile/personal_access_tokens\n" +
                        "and store it in the file " + args.getTokenFile());
                System.exit(1);
            }
        }
    }

    public void execute() throws Exception {
        var tasks = taskCount();
        int charsPerLine;
        if (tasks < 60) {
            charsPerLine = 10;
        } else if (tasks < 150) {
            charsPerLine = 20;
        } else {
            charsPerLine = 50;
        }
        progress = new ProgressTracker(System.out, charsPerLine)
                .usingChar("existing", '-').usingChar("failed", 'X');

        executeTasks();

        printSummary();
    }

    protected abstract int taskCount() throws Exception;

    protected abstract void executeTasks() throws Exception;

    protected void printSummary() {
        progress.printSummary();
    }

    protected Group getGroup() throws GitLabApiException {
        return gitlab.getGroupApi().getGroup(args.getGroup());
    }

    protected String lastPushedCommitBefore(Project project, String branch,
                                            Instant deadline) throws GitLabApiException {
        return lastPushedCommitBefore(project, branch, deadline, e -> true);
    }

    protected String lastPushedCommitBefore(Project project, String branch,
                                            Instant deadline, Predicate<Event> filter) throws GitLabApiException {
        // fetch all push-events on the day of the deadline and earlier
        // (actually, add 1 day to deadline since GitLab ignores time of day)
        var pushes = gitlab.getEventsApi().getProjectEventsStream(project.getId(),
                PUSHED, null, Date.from(deadline.plus(1, DAYS)), null, DESC);

        // filter precisely here:
        return pushes
                .filter(e -> !e.getCreatedAt().after(Date.from(deadline)))
                .filter(filter)
                .map(Event::getPushData)
                .filter(p -> p.getRef().equals(branch))
                .map(PushData::getCommitTo)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }
}
