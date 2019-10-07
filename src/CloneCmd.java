import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static org.eclipse.jgit.api.Git.cloneRepository;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

public class CloneCmd extends Cmd<CloneCmd.Args> {

    private CredentialsProvider credentials;

    public CloneCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");

        credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Paths.get(args.getDestinationDir());
        createDirectories(destDir);

        int cloned = 0;
        for (var project : getProjectsIn(studGroup)) {
            var repoDir = destDir.resolve(project.getName());

            clone(project.getWebUrl(), repoDir);

            cloned++;
            System.out.print(".");
            if (cloned % 80 == 0) {
                System.out.println();
            }
        }
        System.out.printf("Done. %d submissions cloned\n", cloned);
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
                    e.printStackTrace(System.err);
                    System.err.println("Transport exception! Attempts left: " + attempts);
                }
            }
        }
    }

    interface Args extends CmdWithCourseData.Args {
        @Option
        String getGroupName();

        @Option
        String getDestinationDir();
    }
}
