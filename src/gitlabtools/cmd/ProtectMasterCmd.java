package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import org.gitlab4j.api.GitLabApiException;

public class ProtectMasterCmd extends Cmd<ArgsWithProjectAccess> {

    public ProtectMasterCmd(String[] rawArgs) throws Exception {
        super(createCli(ArgsWithProjectAccess.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var api = gitlab.getProtectedBranchesApi();

        var projects = getProjects(args);
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
}
