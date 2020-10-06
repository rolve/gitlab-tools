package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static org.eclipse.jgit.api.Git.cloneRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

/**
 * Publishes the content in a given "template" directory into the root directory
 * of existing repositories. Assumes that a repository needs a single template,
 * unlike {@link PublishEclipseProjectCmd}, which publishes an Eclipse project
 * into a directory within a repository.
 */
public class PublishTemplateCmd extends Cmd<PublishTemplateCmd.Args> {

    private static final int ATTEMPTS = 3;

    public PublishTemplateCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var templateDir = Paths.get(args.getTemplateDir());
        var workDir = createTempDirectory("gitlab-tools");
        workDir.toFile().deleteOnExit();

        var projects = getProjects(args);
        System.out.println("Publishing template to " + projects.size()
                + " repositories...");
        for (var project : projects) {
            try {
                var repoDir = workDir.resolve(project.getName());

                Git git = null;
                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        git = cloneRepository().setURI(project.getWebUrl())
                                .setDirectory(repoDir.toFile())
                                .setCredentialsProvider(credentials).call();
                        // done
                        attempts = 0;
                    } catch (TransportException e) {
                        progress.interrupt();
                        e.printStackTrace(System.out);
                        System.out.println(
                                "Transport exception for " + project.getName()
                                        + "! Attempts left: " + attempts);
                        if (attempts == 0) {
                            throw e;
                        }
                    }
                }

                try (var contents = list(repoDir)) {
                    if (contents.anyMatch(p -> !p.getFileName().toString().equals(".git"))) {
                        progress.advance("existing");
                        continue;
                    }
                }

                copyDir(templateDir, repoDir);
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Publish template").call();
                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        git.push().add("master")
                                .setCredentialsProvider(credentials).call();
                        // done
                        attempts = 0;
                    } catch (TransportException e) {
                        progress.interrupt();
                        e.printStackTrace(System.out);
                        System.out.println(
                                "Transport exception for " + project.getName()
                                        + "! Attempts left: " + attempts);
                        if (attempts == 0) {
                            throw e;
                        }
                    }
                }
                git.close();
                progress.advance();

                Thread.sleep(500);
            } catch (Exception e) {
                progress.advance("failed");
                progress.interrupt();
                System.out.println("Problem with " + project.getName() + ":");
                e.printStackTrace(System.out);
            }
        }
    }

    private static void copyDir(Path src, Path dest) throws IOException {
        Iterable<Path> sources = walk(src).skip(1)::iterator;
        for (var source : sources) {
            copy(source, dest.resolve(src.relativize(source)));
        }
    }

    interface Args extends ArgsWithProjectAccess {
        @Option
        String getTemplateDir();
    }
}
