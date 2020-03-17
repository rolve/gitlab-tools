import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

public class AssignMembersCmd extends Cmd<ArgsWithProjectAccess> {

    public AssignMembersCmd(String[] rawArgs) throws Exception {
        super(createCli(ArgsWithProjectAccess.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, args.getSubgroupName());
        var projects = getProjectsIn(studGroup);

        for (var project : projects) {
            var exists = gitlab.getProjectApi().getMembers(project.getId()).stream()
                    .anyMatch(m -> m.getUsername().equals(project.getName()));
            if (!exists) {
                var user = users().stream()
                        .filter(u -> u.getUsername().equals(project.getName()))
                        .findFirst();
                if (user.isPresent()) {
                    gitlab.getProjectApi().addMember(project.getId(), user.get().getId(), DEVELOPER);
                    progress.advance();
                } else {
                    progress.advance("failed");
                    progress.interrupt();
                    System.out.printf("Error: user %s not among Gitlab users\n", project.getName());
                }
            } else {
                progress.advance("existing");
            }
        }
    }
}
