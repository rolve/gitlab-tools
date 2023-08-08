package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.Collections.reverseOrder;
import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

public class ExportSourcesCmd extends CmdForProjects<ExportSourcesCmd.Args> {

    private CredentialsProvider credentials;

    public ExportSourcesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Path.of(args.getDestinationDir());
        createDirectories(destDir);

        var projects = getProjects();
        var numbers = range(0, projects.size())
                .mapToObj(Integer::toString).collect(toList());
        shuffle(numbers);
        var newNames = numbers.iterator();

        System.out.println("Exporting sources of " + projects.size() + " repositories...");
        for (var project : projects) {
            var repoDir = destDir.resolve(project.getName());

            checkout(project.getWebUrl(), repoDir);
            deleteRecursive(repoDir.resolve(".git"));
            removeNonSubmissions(repoDir);
            removeNonSources(repoDir);
            removeEmptyDirs(repoDir);

            move(repoDir, destDir.resolve(newNames.next()));
            progress.advance();
        }
    }

    private void checkout(String projectUrl, Path repoDir) throws GitAPIException, IOException {
        int attempts = 2;
        while (attempts-- > 0) {
            try {
                var clone = true;
                if (exists(repoDir)) {
                    var success = tryPull(repoDir);
                    clone = !success;
                }
                if (clone) {
                    cloneRepository()
                            .setURI(projectUrl)
                            .setDirectory(repoDir.toFile())
                            .setCredentialsProvider(credentials)
                            .call()
                            .close();
                    progress.additionalInfo("newly cloned");
                }
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

    private boolean tryPull(Path repoDir) throws IOException {
        try (Git git = open(repoDir.toFile())) {
            git.pull()
                    .setCredentialsProvider(credentials)
                    .call();
            return true;
        } catch (Exception e) {
            // something went wrong before, delete everything and clone
            e.printStackTrace();
            System.err.println("Deleting " + repoDir + " and trying a fresh clone...");
            deleteRecursive(repoDir);
            return false;
        }
    }

    private void removeNonSubmissions(Path dir) throws IOException {
        try (var paths = list(dir)) {
            var nonSubs = paths.filter(p ->
                    !isDirectory(p) ||
                    !p.getFileName().toString().matches("u\\d+"));
            for (var p : iterable(nonSubs)) {
                deleteRecursive(p);
            }
        }
    }

    private void deleteRecursive(Path path) throws IOException {
        try (var paths = Files.walk(path).sorted(reverseOrder())) {
            for (var p : iterable(paths)) {
                p.toFile().setWritable(true);
                delete(p);
            }
        }
    }

    private void removeNonSources(Path dir) throws IOException {
        try (var paths = walk(dir)) {
            var filtered = paths.filter(p ->
                    isRegularFile(p) &&
                    !p.toString().toLowerCase().endsWith(".java"));
            for (var p : iterable(filtered)) {
                delete(p);
            }
        }
    }

    private void removeEmptyDirs(Path dir) throws IOException {
        try (var paths = walk(dir)) {
            var dirs = paths.sorted(reverseOrder())
                    .filter(Files::isDirectory);
            for (var d : iterable(dirs)) {
                try (var list = list(d)) {
                    if (list.findAny().isEmpty()) {
                        delete(d);
                    }
                }
            }
        }
    }

    /**
     * Helper method to be able to iterate over a stream in a for loop, which is
     * useful if the body throws a checked exception.
     */
    private static <T> Iterable<T> iterable(Stream<T> stream) {
        return stream::iterator;
    }

    interface Args extends CmdForProjects.Args {
        @Option
        String getDestinationDir();
    }
}
