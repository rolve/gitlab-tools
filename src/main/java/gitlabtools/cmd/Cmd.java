package gitlabtools.cmd;

import gitlabtools.ProgressTracker;
import gitlabtools.auth.AuthenticationException;
import gitlabtools.auth.TokenCreationException;
import gitlabtools.auth.TokenCreator;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.Group;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.file.Files.readAllLines;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;

public abstract class Cmd<A extends Args> {

    public interface Constructor {
        Cmd<?> construct(String[] args) throws Exception;
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
        progress = new ProgressTracker()
                .usingChar("existing", '-').usingChar("failed", 'X');
        doExecute();
        progress.printSummary();
    }

    protected abstract void doExecute() throws Exception;

    protected Group getGroup() throws GitLabApiException {
        return gitlab.getGroupApi().getGroup(args.getGroup());
    }

    protected static <E> Stream<E> stream(Pager<E> pager) {
        var pages = StreamSupport.stream(spliteratorUnknownSize(pager, ORDERED), false);
        return pages.flatMap(List::stream);
    }
}
