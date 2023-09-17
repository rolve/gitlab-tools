package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.RepositoryFile;

import java.nio.file.Path;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.Objects.requireNonNullElse;

public class PublishFileCmd extends CmdForProjects<PublishFileCmd.Args> {

    private final Path file;
    private final byte[] content;

    public PublishFileCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
        file = Path.of(args.getFile()).toAbsolutePath();
        if (!exists(file)) {
            throw new ArgumentValidationException("File " + file + " not found");
        } else if (!isRegularFile(file)) {
            throw new ArgumentValidationException("File " + file + " is not a regular file");
        }
        content = readAllBytes(file);
    }

    @Override
    protected void doExecute() throws Exception {
        var destDir = (args.getDestDir().replaceAll("/$", "") + "/").replaceAll("^/", "");
        var destFile = destDir + file.getFileName();
        var message = requireNonNullElse(args.getCommitMessage(), "Publish " + file.getFileName());

        var fileApi = gitlab.getRepositoryFileApi();
        for (var project : getProjects()) {
            var branch = requireNonNullElse(args.getBranch(), project.getDefaultBranch());
            if (fileApi.getOptionalFile(project, destFile, branch).isPresent()) {
                progress.advance("existing");
                continue;
            }

            var repoFile = new RepositoryFile();
            repoFile.setFilePath(destFile);
            repoFile.encodeAndSetContent(content);
            fileApi.createFile(project, repoFile, branch, message);
            progress.advance();
        }
    }

    public interface Args extends CmdForProjects.Args {
        @Option
        String getFile();

        @Option(defaultValue = "/")
        String getDestDir();

        @Option(defaultToNull = true)
        String getCommitMessage();

        /**
         * Must already exist in the GitLab repo. If not set, the default
         * branch configured in GitLab is used.
         */
        @Option(defaultToNull = true)
        String getBranch();
    }
}
