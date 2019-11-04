import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.GitLabApiException;

import com.lexicalscope.jewel.cli.Option;

public class ProtectMasterCmd extends Cmd<ProtectMasterCmd.Args> {

    public ProtectMasterCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var projects = getProjectsIn(studGroup);
        var api = gitlab.getProtectedBranchesApi();

        System.out.printf("Protecting 'master' branch for %d projects...\n", projects.size());

        int performed = 0;
        int error = 0;
        for (var project : projects) {
            try {
                // remove all protected branches first
                var branches = api.getProtectedBranches(project.getId());
                for (var branch : branches) {
                    api.unprotectBranch(project.getId(), branch.getName());
                }

                // then protect 'master' from force-pushing (but DEVELOPERs may push and merge)
                api.protectBranch(project.getId(), "master", DEVELOPER, DEVELOPER);
                performed++;
            } catch (GitLabApiException e) {
                e.printStackTrace();
                error++;
            }
        }
        System.out.printf("Done. %d branches protected, %d errors\n", performed, error);
    }

    public interface Args extends Cmd.Args {
        @Option
        String getGroupName();
    }
}
