package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.ProtectedBranch;

import java.util.Optional;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;

public class ProtectBranchCmd extends Cmd<ProtectBranchCmd.Args> {

    private final String branch;
    private final AccessLevel access;

    public ProtectBranchCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
        branch = args.getBranch();
        access = AccessLevel.valueOf(args.getBranchAccess().toUpperCase());
    }

    @Override
    protected void doExecute() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();
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
