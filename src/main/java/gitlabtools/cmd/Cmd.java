package gitlabtools.cmd;

import gitlabtools.Cache;
import gitlabtools.ProgressTracker;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.file.Files.readAllLines;
import static java.util.Comparator.comparing;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;

public abstract class Cmd<A extends Args> {

    private static final Cache<Optional<Group>> groupCache = new Cache<>();
    private static final Cache<List<Project>> projectsCache = new Cache<>();

    protected final A args;
    protected final String token;
    protected final GitLabApi gitlab;

    protected ProgressTracker progress;

    private List<User> users = null;

    public Cmd(A args) throws IOException {
        this.args = args;

        token = readAllLines(Paths.get(args.getTokenFile())).get(0);
        gitlab = new GitLabApi(args.getGitlabUrl(), token);
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

    protected List<Project> getProjectsIn(Group group) throws GitLabApiException {
        var key = new Cache.Key(args.getGitlabUrl(), group.getId());
        return projectsCache.update(key, () -> {
            System.out.println("Fetching projects from group " + group.getName() + "...");
            return stream(gitlab.getGroupApi().getProjects(group.getId(), 100))
                    .sorted(comparing(Project::getName))
                    .collect(toList());
        });
    }

    protected Project getProject(Group group, String projectName) throws GitLabApiException {
        return stream(gitlab.getGroupApi().getProjects(group.getId(), 100))
                .filter(p -> p.getName().equals(projectName))
                .findFirst().get();
    }

    protected List<Project> getProjects(Args args)
            throws GitLabApiException {
        var group = getGroup(args.getGroupName());
        var subgroup = getSubgroup(group, args.getSubgroupName());
        return getProjectsIn(subgroup);
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
        return () -> stream.iterator();
    }
}
