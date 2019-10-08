import static java.nio.file.Files.readAllLines;
import static java.util.Comparator.comparing;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.Pager;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;

import com.lexicalscope.jewel.cli.Option;

public abstract class Cmd<A extends Cmd.Args> {

    protected final A args;
    protected final String token;
    protected final GitLabApi gitlab;

    private List<User> users = null;
    private Map<String, User> nameToUserMap = null;

    public Cmd(A args) throws Exception {
        this.args = args;

        token = readAllLines(Paths.get(args.getTokenFile())).get(0);
        gitlab = new GitLabApi(args.getGitlabUrl(), token);
    }

    abstract void call() throws Exception;

    protected List<User> users() {
        if (users == null) {
            fetchUsers();
        }
        return users;
    }

    protected Map<String, User> nameToUserMap() {
        if (users == null) {
            fetchUsers();
        }
        return nameToUserMap;
    }

    private void fetchUsers() {
        System.out.println("Fetching users from Gitlab...");

        users = new ArrayList<>();
        nameToUserMap = new HashMap<>();

        var duplicateNames = new HashSet<String>();
        try {
            streamPager(gitlab.getUserApi().getUsers(100)).forEach(user -> {
                users.add(user);
                if (nameToUserMap.put(user.getName(), user) != null) {
                    duplicateNames.add(user.getName());
                }
            });
        } catch (GitLabApiException e) {
            throw new RuntimeException(e);
        }
        nameToUserMap.keySet().removeAll(duplicateNames);
        System.out.printf("%d users fetched\n", users.size());
    }

    protected Group getGroup(String groupName) throws GitLabApiException {
        return streamPager(gitlab.getGroupApi().getGroups(100))
                .filter(g -> g.getName().equals(groupName))
                .findFirst().get();
    }

    protected Group getSubGroup(Group group, String subGroupName) throws GitLabApiException {
        return streamPager(gitlab.getGroupApi().getSubGroups(group.getId(), 100))
                .filter(g -> g.getName().equals(subGroupName))
                .findFirst().get();
    }

    protected List<Project> getProjectsIn(Group group) throws GitLabApiException {
        System.out.println("Fetching projects from group " + group.getName() + "...");
        return streamPager(gitlab.getGroupApi().getProjects(group.getId(), 100))
                .sorted(comparing(Project::getName))
                .collect(toList());
    }

    protected Project getProject(Group group, String projectName) throws GitLabApiException {
        return streamPager(gitlab.getGroupApi().getProjects(group.getId(), 100))
                .filter(p -> p.getName().equals(projectName))
                .findFirst().get();
    }

    protected static <E> Stream<E> streamPager(Pager<E> pager) {
        var pages = stream(spliteratorUnknownSize(pager, ORDERED), false);
        return pages.flatMap(List::stream);
    }

    /**
     * Helper method to be able to iterate over a stream in a for loop, which is
     * useful if the body throws a checked exception.
     */
    protected static <T> Iterable<T> iterable(Stream<T> stream) {
        return () -> stream.iterator();
    }

    public interface Args {
        @Option(defaultValue = "https://gitlab.inf.ethz.ch/")
        String getGitlabUrl();

        @Option(defaultValue = "token.txt")
        String getTokenFile();
    }
}
