import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class CreateProjectsCmd extends CmdWithEdoz<CreateProjectsCmd.Args> {

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var groupId = getGroupId(args.getGroupName());
        
        System.out.printf("Creating projects for %d students...\n", students.size());
        var projects = gitlab.getProjectApi();
        for (int s = 0; s < students.size(); s++) {
            var student = students.get(s);
            if (student.nethz.isPresent()) {
                Project project = projects.createProject(groupId, student.nethz.get());
                gitlab.getProjectApi().addMember(project.getId(), student.user.get().getId(), DEVELOPER);
            } else {
                System.err.printf("Warning: no project created for %s\n", student.firstAndLastName);
            }
            if (s % 10 == 9) {
                System.out.printf("%d projects created\n", s + 1);
            }
        }
        System.out.println("Done.");
    }

    private int getGroupId(String groupName) throws GitLabApiException {
        return gitlab.getGroupApi().getGroups(groupName).stream()
                .filter(g -> g.getName().equals(groupName))
                .findFirst().get().getId();
    }

    public interface Args extends CmdWithEdoz.Args {
        @Option
        String getGroupName();
    }
}
