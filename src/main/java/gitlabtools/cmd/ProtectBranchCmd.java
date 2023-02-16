package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.AccessLevel;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;

public class ProtectBranchCmd extends Cmd<ProtectBranchCmd.Args> {

    public ProtectBranchCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();
        var branch = args.getBranch();
        var access = AccessLevel.valueOf(args.getBranchAccess().toUpperCase());
        for (var project : getProjects(args)) {
            // remove protected branch first, in case it already exists
            if (branchApi.getOptionalProtectedBranch(project, branch).isPresent()) {
                branchApi.unprotectBranch(project, branch);
            }
            branchApi.protectBranch(project, branch, access, access);
            progress.advance();
        }
    }

    public interface Args extends gitlabtools.cmd.Args {
        @Option
        String getBranch();
        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getBranchAccess();
    }
}
