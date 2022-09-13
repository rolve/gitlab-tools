package gitlabtools.cmd;

import gitlabtools.Cache;
import gitlabtools.ProgressTracker;
import gitlabtools.auth.AuthenticationException;
import gitlabtools.auth.TokenCreationException;
import gitlabtools.auth.TokenCreator;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.file.Files.readAllLines;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;

public abstract class Cmd<A extends Args> {

    private static final Cache<Optional<Group>> groupCache = new Cache<>();

    protected final A args;
    protected final String token;
    protected final GitLabApi gitlab;

    protected ProgressTracker progress;

    private List<User> users = null;

    public Cmd(A args) throws IOException {
        this.args = args;

        var tokenFile = Path.of(args.getTokenFile());
        if (Files.notExists(tokenFile)) {
            promptCreateToken();
        }
        token = readAllLines(tokenFile).get(0);
        gitlab = new GitLabApi(args.getGitlabUrl(), token);
    }

    private void promptCreateToken() throws IOException {
        var shortUrl = args.getGitlabUrl()
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
        var creator = new TokenCreator(args.getGitlabUrl());
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
                var slash = args.getGitlabUrl().endsWith("/") ? "" : "/";
                System.out.println("\nCould not create token. Create the token manually here:\n" +
                        args.getGitlabUrl() + slash + "-/profile/personal_access_tokens\n" +
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

    protected List<User> users() {
        if (users == null) {
            fetchUsers();
        }
        return users;
    }

    private void fetchUsers() {
        if (progress != null) {
            progress.interrupt();
        }
        System.out.println("Fetching users from Gitlab...");

        try {
            users = stream(gitlab.getUserApi().getUsers(100))
                    .collect(toList());
        } catch (GitLabApiException e) {
            throw new RuntimeException(e);
        }
        System.out.printf("%d users fetched\n", users.size());
    }

    protected Group getGroup(String groupName) throws GitLabApiException {
        var key = new Cache.Key(args.getGitlabUrl(), groupName);
        return groupCache.update(key, () ->
                stream(gitlab.getGroupApi().getGroups(100))
                        .filter(g -> g.getName().equals(groupName))
                        .findFirst()).get();
    }

    protected Group getSubgroup(Group group, String subgroupName) throws GitLabApiException {
        var key = new Cache.Key(args.getGitlabUrl(), group.getId() + "#" + subgroupName);
        var subgroup = groupCache.update(key, () ->
                stream(gitlab.getGroupApi().getSubGroups(group.getId(), 100))
                        .filter(g -> g.getName().equals(subgroupName))
                        .findFirst());
        if (subgroup.isPresent()) {
            return subgroup.get();
        }

        System.out.print("Subgroup " + subgroupName + " inside "
                + group.getName() + " does not exist. Create it? [Y/n] ");
        var reply = new Scanner(System.in).nextLine().strip().toLowerCase();
        if (reply.isEmpty() || reply.charAt(0) != 'n') {
            var newSubgroup = gitlab.getGroupApi().addGroup(subgroupName, subgroupName,
                    null, group.getVisibility(), null, null, group.getId());
            groupCache.update(key, () -> Optional.of(newSubgroup));
            return newSubgroup;
        } else {
            System.exit(0);
            return null;
        }
    }

    protected List<Project> getProjects(Args args)
            throws GitLabApiException {
        var group = getGroup(args.getGroupName());
        var subgroup = getSubgroup(group, args.getSubgroupName());
        return gitlab.getGroupApi().getProjects(subgroup);
    }

    protected static <E> Stream<E> stream(Pager<E> pager) {
        var pages = StreamSupport.stream(spliteratorUnknownSize(pager, ORDERED), false);
        return pages.flatMap(List::stream);
    }

    /**
     * Helper method to be able to iterate over a stream in a for loop, which is
     * useful if the body throws a checked exception.
     */
    protected static <T> Iterable<T> iterable(Stream<T> stream) {
        return stream::iterator;
    }
}
