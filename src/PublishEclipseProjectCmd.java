import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

/**
 * Publishes an Eclipse project into an existing repository (GitLab "project").
 * This command assumes that a repository contains multiple Eclipse projects,
 * each in a directory with a name matching the Eclipse project name.
 */
public class PublishEclipseProjectCmd
        extends Cmd<PublishEclipseProjectCmd.Args> {

    private static final int ATTEMPTS = 3;

    public PublishEclipseProjectCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, args.getSubgroupName());

        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var sourceDir = Paths.get(args.getProjectDir());
        var workDir = Paths.get(args.getWorkDir());
        createDirectories(workDir);

        var eclipseProjectName = sourceDir.getFileName().toString();

        var projects = getProjectsIn(studGroup);
        System.out.println("Publishing " + sourceDir.getFileName() + " to "
                + projects.size() + " repositories...");
        for (var project : projects) {
            try {
                var repoDir = workDir.resolve(project.getName());
                var destDir = repoDir.resolve(eclipseProjectName);
                if (exists(destDir)) {
                    progress.advance("existing");
                    continue;
                }

                Git git = null;
                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        if (exists(repoDir)) {
                            git = open(repoDir.toFile());
                            git.pull()
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
                        // done
                        attempts = 0;
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
                if (exists(destDir)) {
                    progress.advance("existing");
                    continue;
                }

                copyDir(sourceDir, destDir);
                if (!eclipseProjectName.endsWith("-sol")
                        && !eclipseProjectName.endsWith(" Lösungen")) {
                    renameProject(destDir, project.getName());
                }
                git.add()
                        .addFilepattern(".")
                        .call();
                git.commit()
                        .setMessage("Publish " + eclipseProjectName)
                        .call();
                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        git.push()
                                .add("master")
                                .setCredentialsProvider(credentials)
                                .call();
                        progress.advance();
                        // done
                        attempts = 0;
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
                git.close();

                Thread.sleep(500);
            } catch (Exception e) {
                progress.advance("failed");
                progress.interrupt();
                System.out.println("Problem with " + project.getName() + ":");
                e.printStackTrace(System.out);
            }
        }
    }

    private void renameProject(Path projectDir, String username) throws IOException {
        var projectFile = projectDir.resolve(".project");
        var content = lines(projectFile).collect(joining("\n"));
        var newContent = content.replace("REPLACEME", username);
        if (newContent.equals(content)) {
            throw new AssertionError("REPLACEME not found");
        }
        write(projectFile, List.of(newContent));
    }

    private void copyDir(Path src, Path dest) throws IOException {
        walk(src).forEach(source -> {
            try {
                copy(source, dest.resolve(src.relativize(source)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    interface Args extends ArgsWithProjectAccess {
        @Option
        String getWorkDir();

        @Option
        String getProjectDir();
    }
}
