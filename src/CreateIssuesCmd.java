import static com.lexicalscope.jewel.cli.CliFactory.createCli;

import org.gitlab4j.api.GitLabApiException;

import com.lexicalscope.jewel.cli.Option;

public class CreateIssuesCmd extends CmdWithEdoz<CreateIssuesCmd.Args> {

    public CreateIssuesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var projectId = getProjectId(args.getProjectName());

        System.out.printf("Creating issues for %d students...\n", students.size());
        var issues = gitlab.getIssuesApi();
        for (int s = 0; s < students.size(); s++) {
            var student = students.get(s);
            issues.createIssue(projectId, student.toString(), student.legi);
            if (s % 10 == 9) {
                System.out.printf("%d issues created\n", s + 1);
            }
        }
        System.out.println("Done.");
    }

    private int getProjectId(String projectName) throws GitLabApiException {
        return gitlab.getProjectApi().getProjects(projectName).stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst().get().getId();
    }

    public interface Args extends CmdWithEdoz.Args {
        @Option
        String getProjectName();
    }
}
