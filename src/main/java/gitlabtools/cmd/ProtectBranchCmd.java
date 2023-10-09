package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.AccessLevel;

import java.io.IOException;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;

public class ProtectBranchCmd extends CmdForProjects<ProtectBranchCmd.Args> {

    public ProtectBranchCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void executeTasks() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();
        var branch = args.getBranch();
        var access = AccessLevel.valueOf(args.getBranchAccess().toUpperCase());
        for (var project : getProjects()) {
            // remove protected branch first, in case it already exists
            if (branchApi.getOptionalProtectedBranch(project, branch).isPresent()) {
                branchApi.unprotectBranch(project, branch);
            }
            branchApi.protectBranch(project, branch, access, access);
            progress.advance();
        }
    }

    public interface Args extends CmdForProjects.Args {
        @Option
        String getBranch();
        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getBranchAccess();
    }
}
