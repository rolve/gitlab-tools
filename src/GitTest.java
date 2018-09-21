import static java.lang.Math.random;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.util.Arrays.asList;
import static java.util.Comparator.reverseOrder;
import static org.eclipse.jgit.api.Git.cloneRepository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GitTest {

    public static void main(String[] args) throws IOException, GitAPIException, URISyntaxException {
        var token = readAllLines(Paths.get("token.txt")).get(0);
        CredentialsProvider credentials = new UsernamePasswordCredentialsProvider("mfaes", token);

        var dir = Paths.get("temp");
        if (exists(dir)) {
            walk(dir).sorted(reverseOrder()).map(Path::toFile).forEach(File::delete);
        }

        var git = cloneRepository()
                .setURI("https://gitlab.inf.ethz.ch/COURSE-EPROG2018/playground/mfaes-test.git")
                .setDirectory(dir.toFile())
                .setCredentialsProvider(credentials)
                .call();

        write(dir.resolve("file-" + random() + ".txt"), asList("Meh"));
        git.add().addFilepattern(".").call();

        git.commit()
                .setMessage("Nothing")
                .call();
        git.push()
                .add("master")
                .setCredentialsProvider(credentials)
                .call();

        git.close();
    }
}
