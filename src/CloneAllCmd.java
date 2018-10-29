import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.Constants.ActionType;
import org.gitlab4j.api.Constants.SortOrder;
import org.gitlab4j.api.models.Event;

import com.lexicalscope.jewel.cli.Option;

public class CloneAllCmd extends Cmd<CloneAllCmd.Args> {

	public CloneAllCmd(String[] rawArgs) throws Exception {
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
		for (var project : getProjectsIn(studGroup)) {
			var repoDir = workDir.resolve(project.getName());
			
			// add 1 day to deadline since gitlab ignores time of day
			Calendar tempCal = Calendar.getInstance();
			tempCal.setTime(deadline);
			tempCal.add(Calendar.DATE, 1);
			
			// fetch all push-events the day of the deadline (and before)
			var pager = gitlab.getEventsApi().getProjectEvents(project.getId(), ActionType.PUSHED, null, tempCal.getTime(), null, SortOrder.DESC, 100);
			
			Optional<Event> lastEvent = streamPager(pager)
				.filter(e -> e.getCreatedAt().before(deadline))
				.filter(e -> e.getPushData().getRef().equals("master"))
				.findFirst();
			
			if (!lastEvent.isPresent()) {
				System.err.printf("Skipping %s, no push events found before date.\n", project.getName());
				continue;
			}
			
			Git git;
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
			}
			
			// go to last commit befor the deadline
			String lastCommitSHA = lastEvent.get().getPushData().getCommitTo();
			
			git.checkout()
				.setName(lastCommitSHA)
				.call();
			
			cloned++;
			System.out.print(".");

			git.close();
		}
		System.out.printf("Done. %d repos cloned\n", cloned);
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
