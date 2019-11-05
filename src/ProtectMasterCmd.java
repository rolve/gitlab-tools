import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.GitLabApiException;

import com.lexicalscope.jewel.cli.Option;

public class ProtectMasterCmd extends Cmd<ProtectMasterCmd.Args> {

    public ProtectMasterCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var projects = getProjectsIn(studGroup);
        var api = gitlab.getProtectedBranchesApi();

        System.out.println("Protecting 'master' branch for " + projects.size() + " projects...");
        for (var project : projects) {
            try {
                // remove all protected branches first
                var branches = api.getProtectedBranches(project.getId());
                for (var branch : branches) {
                    api.unprotectBranch(project.getId(), branch.getName());
                }

                // then protect 'master' from force-pushing (but DEVELOPERs may push and merge)
                api.protectBranch(project.getId(), "master", DEVELOPER, DEVELOPER);
                progress.advance();
            } catch (GitLabApiException e) {
                progress.advance("failed");
                progress.interrupt();
                e.printStackTrace(System.out);
            }
        }
    }

    public interface Args extends Cmd.Args {
        @Option
        String getGroupName();
    }
}
