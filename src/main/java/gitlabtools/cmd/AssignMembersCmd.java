package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toList;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.User;

import java.io.IOException;
import java.util.List;

public class AssignMembersCmd extends CmdForProjects<AssignMembersCmd.Args> {

    private List<User> users = null;

    public AssignMembersCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        for (var project : getProjects()) {
            var name = project.getName();
            if (args.isWithProjectNamePrefix()) {
                var parts = name.split("_", 2);
                if (parts.length != 2) {
                    throw new AssertionError("unexpected project name " + name + "; expected prefix and _");
                }
                name = parts[1];
            }
            if (args.isTeamProjects()) {
                for (var member : name.split("_")) {
                    addMember(project, member);
                }
            } else {
                addMember(project, name);
            }
        }
    }

    private void addMember(Project project, String username) throws Exception {
        var exists = gitlab.getProjectApi().getMembers(project).stream()
                .map(Member::getUsername)
                .anyMatch(username::equals);
        if (!exists) {
            var user = users().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
            if (user.isPresent()) {
                try {
                    gitlab.getProjectApi().addMember(project.getId(), user.get().getId(), DEVELOPER);
                    progress.advance();
                } catch (GitLabApiException e) {
                    progress.advance("failed");
                    progress.interrupt();
                    System.out.printf("Error: could not add %s as a member. Are they member of the containing group?\n", username);
                    e.printStackTrace();
                }
            } else {
                progress.advance("failed");
                progress.interrupt();
                System.out.printf("Error: user %s not among GitLab users\n", username);
            }
        } else {
            progress.advance("existing");
        }
    }

    private List<User> users() {
        if (users == null) {
            fetchUsers();
        }
        return users;
    }

    private void fetchUsers() {
        if (progress != null) {
            progress.interrupt();
        }
        System.out.println("Fetching users from GitLab...");

        try {
            users = stream(gitlab.getUserApi().getUsers(100))
                    .collect(toList());
        } catch (GitLabApiException e) {
            throw new RuntimeException(e);
        }
        System.out.printf("%d users fetched\n", users.size());
    }

    interface Args extends CmdForProjects.Args {
        @Option
        boolean isTeamProjects();
    }
}
