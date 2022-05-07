package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static org.eclipse.jgit.api.Git.cloneRepository;

import java.io.IOException;
import java.nio.file.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

/**
 * Publishes the content in a given "template" directory into a directory of
 * existing repositories.
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

                Path destDir;
                if (args.getDestDir() == null) {
                    try (var contents = list(repoDir)) {
                        if (contents.anyMatch(p -> !p.getFileName().toString().equals(".git"))) {
                            progress.advance("existing");
                            continue;
                        }
                    }
                    destDir = repoDir;
                } else {
                    destDir = repoDir.resolve(args.getDestDir());
                    if (Files.exists(destDir)) {
                        progress.advance("existing");
                        continue;
                    }
                    createDirectories(destDir);
                }

                copyDir(templateDir, destDir, args.getIgnorePattern());
                git.add().addFilepattern(".").call();
                var message = "Publish " + (args.getDestDir() == null ? "template" : args.getDestDir());
                git.commit().setMessage(message).call();
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

    private static void copyDir(Path src, Path dest, String ignorePattern) throws IOException {
        PathMatcher matcher = null;
        if (ignorePattern != null) {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + ignorePattern);
        }
        Iterable<Path> sources = walk(src).skip(1)::iterator;
        for (var source : sources) {
            var rel = src.relativize(source);
            if (matcher == null || !matcher.matches(rel)) {
                copy(source, dest.resolve(rel));
            }
        }
    }

    interface Args extends gitlabtools.cmd.Args {
        @Option
        String getTemplateDir();

        @Option(defaultToNull = true)
        String getDestDir();

        /**
         * A GLOB pattern that is applied to the relative path of each file
         * in the template directory.
         */
        @Option(defaultToNull = true)
        String getIgnorePattern();
    }
}
