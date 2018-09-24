import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.Files.readAllLines;
import static java.util.stream.Collectors.joining;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.User;

import com.lexicalscope.jewel.cli.Option;

public abstract class Cmd<A extends Cmd.Args> {

    protected final A args;
    protected final GitLabApi gitlab;
    protected final Map<String, User> users = new HashMap<>();

    public Cmd(A args) throws Exception {
        this.args = args;

        var token = readAllLines(Paths.get(args.getTokenFile())).get(0);
        gitlab = new GitLabApi("https://gitlab.inf.ethz.ch/", token);
        fetchUsers();
    }

    private void fetchUsers() throws GitLabApiException {
        System.out.println("Fetching users from Gitlab...");
        var duplicateNames = new HashSet<String>();
        gitlab.getUserApi().getUsers(MAX_VALUE).forEachRemaining(page -> {
            page.forEach(user -> {
                if (users.put(user.getName(), user) != null) {
                    duplicateNames.add(user.getName());
                }
            });
        });
        users.keySet().removeAll(duplicateNames);
        System.out.printf("%d users fetched\n", users.size());
        if (!duplicateNames.isEmpty()) {
            var dups = duplicateNames.stream().collect(joining(", "));
            System.err.printf("Warning: multiple users found for the following names: %s!\n", dups);
        }
    }

    abstract void call() throws Exception;

    protected Group getGroup(String groupName) throws GitLabApiException {
        return gitlab.getGroupApi().getGroups().stream()
                .filter(g -> g.getName().equals(groupName))
                .findFirst().get();
    }

    protected Group getSubGroup(Group group, String subGroupName) throws GitLabApiException {
        return gitlab.getGroupApi().getSubGroups(group.getId()).stream()
                .filter(g -> g.getName().equals(subGroupName))
                .findFirst().get();
    }

    public interface Args {
        @Option(defaultValue = {"token.txt"})
        String getTokenFile();
    }
}
