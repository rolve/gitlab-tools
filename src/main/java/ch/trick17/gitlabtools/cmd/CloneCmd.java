package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Path;

import static ch.trick17.gitlabtools.cmd.GitUtils.checkOutRemoteBranch;
import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

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
            var branch = requireNonNullElse(args.getBranch(), project.getDefaultBranch());

            for (int attempts = ATTEMPTS; attempts-- > 0; ) {
                Git git = null;
                try {
                    if (exists(repoDir)) {
                        git = open(repoDir.toFile());
                        git.fetch()
                                .setCredentialsProvider(credentials)
                                .call();
                    } else {
                        git = cloneRepository()
                                .setURI(project.getWebUrl())
                                .setDirectory(repoDir.toFile())
                                .setCredentialsProvider(credentials)
                                .call();
                        progress.additionalInfo("newly cloned");
                    }

                    if (git.getRepository().findRef("origin/" + branch) == null) {
                        progress.advance("failed");
                        progress.interrupt();
                        System.out.println("Remote branch " + branch + " not found for " + project.getName());
                        break;
                    }

                    checkOutRemoteBranch(git, branch);
                    progress.advance();
                    break;
                } catch (RefNotFoundException e) {
                    progress.advance("failed");
                    progress.interrupt();
                    System.out.println("Branch " + branch + " not found for " + project.getName());
                    break;
                } catch (TransportException e) {
                    progress.interrupt();
                    e.printStackTrace(System.out);
                    System.out.println("Transport exception for " + project.getName() +
                                       "! Attempts left: " + attempts);
                    if (attempts == 0) {
                        throw e;
                    }
                } finally {
                    if (git != null) {
                        git.close();
                    }
                }
            }
        }
    }

    interface Args extends CmdForProjects.Args {
        @Option
        String getDestDir();

        /**
         * The branch to check out. If unspecified, the default branch of each
         * project will be checked out.
         */
        @Option(defaultToNull = true)
        String getBranch();
    }
}
