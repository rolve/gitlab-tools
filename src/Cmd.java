import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.Files.readAllLines;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
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
        gitlab.getUserApi().getUsers(MAX_VALUE).forEachRemaining(page -> {
            page.forEach(user -> {
                if (users.put(user.getName(), user) != null) {
                    System.err.printf("Warning: multiple users with name %s!\n", user.getName());
                }
            });
        });
        System.out.printf("%d users fetched\n", users.size());
    }

    abstract void call() throws Exception;

    public interface Args {
        @Option(defaultValue = {"token.txt"})
        String getTokenFile();
    }
}
