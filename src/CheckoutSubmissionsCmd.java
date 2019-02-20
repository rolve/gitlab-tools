import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;
import static org.gitlab4j.api.Constants.ActionType.PUSHED;
import static org.gitlab4j.api.Constants.SortOrder.DESC;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

/**
 * Clones all 
 */
public class CheckoutSubmissionsCmd extends Cmd<CheckoutSubmissionsCmd.Args> {

	public CheckoutSubmissionsCmd(String[] rawArgs) throws Exception {
		super(createCli(Args.class).parseArguments(rawArgs));
	}

	@Override
	void call() throws Exception {
		var mainGroup = getGroup(args.getGroupName());
		var studGroup = getSubGroup(mainGroup, "students");
		Date deadline = new SimpleDateFormat("yyyy-MM-dd-HH:mm").parse(args.getDate());
		
		System.out.println(deadline);
		
		var credentials = new UsernamePasswordCredentialsProvider("", token);

		var workDir = Paths.get(args.getWorkDir());
		createDirectories(workDir);

		int cloned = 0;
		int checkedOut = 0;
		for (var project : getProjectsIn(studGroup)) {
			var repoDir = workDir.resolve(project.getName());

			// add 1 day to deadline since gitlab ignores time of day
			Calendar tempCal = Calendar.getInstance();
			tempCal.setTime(deadline);
			tempCal.add(Calendar.DATE, 1);

			// fetch all push-events the day of the deadline (and before)
			var pager = gitlab.getEventsApi().getProjectEvents(project.getId(),
			        PUSHED, null, tempCal.getTime(), null, DESC, 100);

			var lastPush = streamPager(pager)
				.filter(e -> e.getCreatedAt().before(deadline))
				.filter(e -> e.getPushData().getRef().equals("master"))
				.findFirst();

			if (!lastPush.isPresent()) {
				System.err.printf("Skipping %s, no push events found before date.\n", project.getName());
				continue;
			}

			Git git = null;
			int attempts = 2;
			while (attempts-- > 0) {
				try {
					if (exists(repoDir)) {
						git = open(repoDir.toFile());
						// need to switch to master, in case we are in "detached head"
						// state (from previous checkout)
						git.checkout()
							.setName("master")
							.call();
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

					// go to last commit before the deadline
					String lastCommitSHA = lastPush.get().getPushData().getCommitTo();

					git.checkout()
						.setName(lastCommitSHA)
						.call();

					// done
					attempts = 0;
				} catch (TransportException e) {
					e.printStackTrace(System.err);
					System.err.println("Transport exception! Attempts left: " + attempts);
					if (attempts == 0) {
						throw e;
					}
				} finally {
					if (git != null)
						git.close();
				}
			}

			checkedOut++;
			System.out.print(".");
			if (checkedOut % 80 == 0)
				System.out.println();

			Thread.sleep(500);
		}
		System.out.printf("Done. %d submissions checked out (%d newly cloned)\n",
		        checkedOut, cloned);
	}

	interface Args extends Cmd.Args {
		@Option
		String getGroupName();

		@Option
		String getWorkDir();

		@Option
		String getDate();
	}
}
