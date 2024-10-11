package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.RepositoryFile;

import java.io.IOException;
import java.nio.file.Path;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.Objects.requireNonNullElse;

/**
 * Publishes a single given file into all repositories in the given group, using
 * the GitLab file API.
 * <p>
 * If a file with the same path already exists in a repository (in the given
 * branch), the command assumes that the file has been published before and
 * skips the repository.
 */
public class PublishFileCmd extends CmdForProjects<PublishFileCmd.Args> {

    private final Path file;
    private final byte[] content;

    public PublishFileCmd(String[] rawArgs) throws IOException {
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
    protected void executeTasks() throws Exception {
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
        /**
         * The local file system path to the file to publish.
         */
        @Option
        String getFile();

        /**
         * The path of a directory in the GitLab repository into which the file
         * is published, e.g., "foo/bar". If unspecified, the file is published
         * to the root of the repository.
         */
        @Option(defaultValue = "/")
        String getDestDir();

        /**
         * The commit message to use for the commit that publishes the file. If
         * unspecified, a default message is used.
         */
        @Option(defaultToNull = true)
        String getCommitMessage();

        /**
         * The branch to publish the file into. Must already exist in the GitLab
         * repository. If unspecified, the default branch configured in GitLab
         * is used.
         */
        @Option(defaultToNull = true)
        String getBranch();
    }
}
