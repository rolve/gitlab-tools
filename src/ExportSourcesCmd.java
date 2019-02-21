import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.Collections.reverseOrder;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

public class ExportSourcesCmd extends Cmd<ExportSourcesCmd.Args> {

    private CredentialsProvider credentials;
    private int cloned;
    private int exported;

    public ExportSourcesCmd(String[] rawArgs) throws Exception {
		super(createCli(Args.class).parseArguments(rawArgs));
	}

	@Override
	void call() throws Exception {
		var mainGroup = getGroup(args.getGroupName());
		var studGroup = getSubGroup(mainGroup, "students");
		
		credentials = new UsernamePasswordCredentialsProvider("", token);

		var destDir = Paths.get(args.getDestinationDir());
		createDirectories(destDir);

		cloned = 0;
		exported = 0;
		for (var project : getProjectsIn(studGroup)) {
		    var repoDir = destDir.resolve(project.getName());

			checkout(project.getWebUrl(), repoDir);
			deleteRecursive(repoDir.resolve(".git"));
		    removeNonSubmissions(repoDir);
		    removeNonSources(repoDir);
		    removeEmptyDirs(repoDir);

			exported++;
			System.out.print(".");
			if (exported % 80 == 0) {
                System.out.println();
            }
		}
		System.out.printf("Done. %d submissions checked out (%d newly cloned)\n",
		        exported, cloned);
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
        			cloned++;
        		}
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
                if (!list(d).findAny().isPresent()) {
                    delete(d);
                }
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

    private <T> Iterable<T> iterable(Stream<T> stream) {
        return () -> stream.iterator();
    }

	interface Args extends Cmd.Args {
		@Option
		String getGroupName();

		@Option
		String getDestinationDir();
	}
}
