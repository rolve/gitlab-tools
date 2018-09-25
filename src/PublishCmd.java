import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.walk;
import static org.eclipse.jgit.api.Git.cloneRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class PublishCmd extends Cmd<PublishCmd.Args> {

    public PublishCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students-test");

        var credentials = new UsernamePasswordCredentialsProvider("", token);
        
        var sourceDir = Paths.get(args.getSourceDir());
        var dirName = sourceDir.getFileName();

        int created = 0;
        int existing = 0;
        for(Project project : getProjectsIn(studGroup)) {
            var repoDir = createTempDirectory("gitlab-tools");

            Git git = cloneRepository()
                    .setURI(project.getWebUrl())
                    .setDirectory(repoDir.toFile())
                    .setCredentialsProvider(credentials)
                    .call();

            if (exists(repoDir.resolve(dirName))) {
                existing++;
            } else {
                copyDir(sourceDir, repoDir.resolve(dirName));
                created++;
                
                git.add()
                        .addFilepattern(".")
                        .call();
                git.commit()
                        .setMessage("Publish " + dirName)
                        .call();
                git.push()
                        .add("master")
                        .setCredentialsProvider(credentials)
                        .call();
            }
            git.close();
        }
        System.out.printf("Done. %d published, %d already exist.\n", created, existing);
    }

    private void copyDir(Path src, Path dest) throws IOException {
        walk(src).forEach(source -> {
            try {
                copy(source, dest.resolve(src.relativize(source)));
            } catch(IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    interface Args extends Cmd.Args {
        @Option
        String getGroupName();
        
        @Option
        String getSourceDir();
    }
}
