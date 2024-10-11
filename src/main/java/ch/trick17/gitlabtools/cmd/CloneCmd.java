package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Path;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static org.eclipse.jgit.api.Git.cloneRepository;

/**
 * Clones all repositories in the --group into the --destDir directory and
 * optionally checks out a specific branch.
 */
public class CloneCmd extends CmdForProjects<CloneCmd.Args> {

    private static final int ATTEMPTS = 3;

    public CloneCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void executeTasks() throws Exception {
        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Path.of(args.getDestDir());
        createDirectories(destDir);

        var projects = getProjects();
        System.out.println("Cloning " + projects.size() + " projects...");
        for (var project : projects) {
            var repoDir = destDir.resolve(project.getName());

            for (int attempts = ATTEMPTS; attempts-- > 0; ) {
                if (exists(repoDir)) {
                    progress.advance("existing");
                    break;
                }

                try (Git ignored = cloneRepository()
                        .setURI(project.getWebUrl())
                        .setDirectory(repoDir.toFile())
                        .setCredentialsProvider(credentials)
                        .call()) {

                    progress.advance();
                    break;
                } catch (TransportException e) {
                    progress.interrupt();
                    e.printStackTrace(System.out);
                    System.out.println("Transport exception for " + project.getName() +
                                       "! Attempts left: " + attempts);
                    if (attempts == 0) {
                        throw e;
                    }
                }
            }
        }
    }

    interface Args extends CmdForProjects.Args {
        @Option
        String getDestDir();
    }
}
