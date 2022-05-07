package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class AssignMembersCmd extends Cmd<AssignMembersCmd.Args> {

    public AssignMembersCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        for (var project : getProjects(args)) {
            var name = project.getName();
            if (args.isWithProjectNamePrefix()) {
                String[] parts = name.split("_", 2);
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
        var exists = gitlab.getProjectApi().getMembers(project.getId()).stream()
                .map(Member::getUsername)
                .anyMatch(username::equals);
        if (!exists) {
            var user = users().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();
            if (user.isPresent()) {
                gitlab.getProjectApi().addMember(project.getId(), user.get().getId(), DEVELOPER);
                progress.advance();
            } else {
                progress.advance("failed");
                progress.interrupt();
                System.out.printf("Error: user %s not among Gitlab users\n", username);
            }
        } else {
            progress.advance("existing");
        }
    }

    interface Args extends gitlabtools.cmd.Args {
        @Option
        boolean isTeamProjects();
        @Option
        boolean isWithProjectNamePrefix();
    }
}
