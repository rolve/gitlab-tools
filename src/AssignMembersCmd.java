import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class AssignMembersCmd extends Cmd<AssignMembersCmd.Args> {

    public AssignMembersCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var projects = getProjectsIn(studGroup);
        for(Project project : projects) {
            var user = users.stream()
                    .filter(u -> u.getUsername().equals(project.getName()))
                    .findFirst();
            if (user.isPresent()) {
                gitlab.getProjectApi().addMember(project.getId(), user.get().getId(), DEVELOPER);
            } else {
                System.err.printf("Warning: user %s not among Gitlab users\n", project.getName());
            }
        }
    }

    public interface Args extends Cmd.Args {
        @Option
        String getGroupName();
    }
}
