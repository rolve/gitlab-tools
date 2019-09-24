import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.lines;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

public class PublishFastCmd extends Cmd<PublishFastCmd.Args> {

    private static final int ATTEMPTS = 3;

    public PublishFastCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");

        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var sourceDir = Paths.get(args.getProjectDir());
        var workDir = Paths.get(args.getWorkDir());
        createDirectories(workDir);

        var projectName = sourceDir.getFileName();

        int created = 0;
        int existing = 0;
        int cloned = 0;
        for (var project : getProjectsIn(studGroup)) {
            var repoDir = workDir.resolve(project.getName());

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
                        cloned++;
                    }
                    // done
                    attempts = 0;
                } catch (TransportException e) {
                    e.printStackTrace(System.err);
                    System.err.println("Transport exception for " + project.getName() +
                            "! Attempts left: " + attempts);
                    if (attempts == 0) {
                        throw e;
                    }
                }
            }

            var destDir = repoDir.resolve(projectName);
            if (exists(destDir)) {
                existing++;
            } else {
                copyDir(sourceDir, destDir);
                if (!projectName.endsWith("-sol")) {
                    renameProject(destDir, project.getName());
                }
                git.add()
                        .addFilepattern(".")
                        .call();
                git.commit()
                        .setMessage("Publish " + projectName)
                        .call();
                for (int attempts = ATTEMPTS; attempts-- > 0;) {
                    try {
                        git.push()
                                .add("master")
                                .setCredentialsProvider(credentials)
                                .call();
                        created++;
                        attempts = 0;
                    } catch (TransportException e) {
                        e.printStackTrace(System.err);
                        System.err.println("Transport exception for " + project.getName() +
                                "! Attempts left: " + attempts);
                        if (attempts == 0) {
                            throw e;
                        }
                    }
                }
            }
            git.close();

            Thread.sleep(1000);
        }
        System.out.printf("Done. %d published, %d already exist. (%d repos newly cloned)\n", created, existing, cloned);
    }

    private void renameProject(Path projectDir, String nethz) throws IOException {
        var projectFile = projectDir.resolve(".project");
        var content = lines(projectFile).collect(joining("\n"));
        var newContent = content.replace("REPLACEME", nethz);
        if (newContent.equals(content)) {
            throw new AssertionError("REPLACEME not found");
        }
        write(projectFile, asList(newContent));
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

    interface Args extends Cmd.Args {
        @Option
        String getGroupName();

        @Option
        String getWorkDir();

        @Option
        String getProjectDir();
    }
}
