package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static org.eclipse.jgit.api.Git.cloneRepository;

public class CloneCmd extends CmdForProjects<CloneCmd.Args> {

    private CredentialsProvider credentials;

    public CloneCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Paths.get(args.getDestDir());
        createDirectories(destDir);

        var projects = getProjects();
        System.out.println("Cloning " + projects.size() + " projects...");
        for (var project : projects) {
            var repoDir = destDir.resolve(project.getName());
            clone(project.getWebUrl(), repoDir);
            progress.advance();
        }
    }

    private void clone(String projectUrl, Path repoDir) throws GitAPIException {
        int attempts = 2;
        while (attempts-- > 0) {
            try {
                cloneRepository()
                        .setURI(projectUrl)
                        .setDirectory(repoDir.toFile())
                        .setCredentialsProvider(credentials)
                        .call()
                        .close();
                break; // done
            } catch (TransportException e) {
                if (attempts == 0) {
                    throw e;
                } else {
                    progress.interrupt();
                    e.printStackTrace(System.out);
                    System.out.println("Transport exception! Attempts left: " + attempts);
                }
            }
        }
    }

    interface Args extends CmdForProjects.Args {
        @Option
        String getDestDir();
    }
}
