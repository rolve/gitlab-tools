import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

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
        
        int assigned = 0;
        int existing = 0;
        int error = 0;
        for (var project : projects) {
            var exists = gitlab.getProjectApi().getMembers(project.getId()).stream()
                    .anyMatch(m -> m.getUsername().equals(project.getName()));
            if (!exists) {
                var user = users().stream()
                        .filter(u -> u.getUsername().equals(project.getName()))
                        .findFirst();
                if (user.isPresent()) {
                    gitlab.getProjectApi().addMember(project.getId(), user.get().getId(), DEVELOPER);
                    assigned++;
                    if (assigned % 10 == 0) {
                        System.out.printf("%d users assigned\n", assigned);
                    }
                } else {
                    System.err.printf("Warning: user %s not among Gitlab users\n", project.getName());
                    error++;
                }
            } else {
                existing++;
            }
        }
        System.out.printf("Done. %d users assigned, %d already exist, %d errors\n",
                assigned, existing, error);
    }

    public interface Args extends Cmd.Args {
        @Option
        String getGroupName();
    }
}
