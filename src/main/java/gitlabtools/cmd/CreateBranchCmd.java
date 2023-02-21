package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.RepositoryApi;
import org.gitlab4j.api.models.AccessLevel;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;

public class CreateBranchCmd extends Cmd<CreateBranchCmd.Args> {

    public CreateBranchCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var repoApi = gitlab.getRepositoryApi();
        for (var project : getProjects(args)) {
            if (repoApi.getOptionalBranch(project, args.getBranch()).isPresent()) {
                progress.advance("existing");
                continue;
            }

            repoApi.createBranch(project, args.getBranch(), args.getRef());

            var access = AccessLevel.valueOf(args.getBranchAccess().toUpperCase());
            gitlab.getProtectedBranchesApi().protectBranch(project, args.getBranch(), access, access);
            progress.advance();
        }
    }

    public interface Args extends gitlabtools.cmd.Args {
        @Option
        String getBranch();
        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getBranchAccess();
        @Option(defaultValue = "HEAD")
        String getRef();
    }
}
