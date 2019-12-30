import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.gitlab4j.api.Constants.ActionType.PUSHED;
import static org.gitlab4j.api.Constants.SortOrder.DESC;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;

import com.lexicalscope.jewel.cli.Option;

public class SubmissionStatsCmd extends Cmd<SubmissionStatsCmd.Args> {

    private static final Cache<List<Event>> eventCache = new Cache<>();

    public SubmissionStatsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var deadline = new SimpleDateFormat("yyyy-MM-dd-HH:mm")
                .parse(args.getDate());

        System.out.println(deadline);

        var stats = new StringBuilder("\nName\tBefore\tAfter\n");

        var projects = getProjectsIn(studGroup);
        for (var project : projects) {
            int id = project.getId();

            var key = new Cache.Key(args.getGitlabUrl(), id + "#" + PUSHED);
            var events = eventCache.update(key, () ->
                    stream(gitlab.getEventsApi().getProjectEvents(id, PUSHED,
                            null, null, null, DESC, 100)).collect(toList()));

            var lastPush = events.stream()
                    .filter(e -> e.getCreatedAt().before(deadline))
                    .filter(e -> e.getPushData().getRef().equals("master"))
                    .findFirst();

            if (lastPush.isEmpty()) {
                progress.advance("failed");
                progress.interrupt();
                System.out.println("Skipping " + project.getName()
                        + ", no push events found before date.");
                continue;
            }
            var lastId = lastPush.get().getPushData().getCommitTo();

            var allCommits = gitlab.getCommitsApi().getCommits(id, "master",
                    null, null, args.getProjectDir() + "/", 100);
            var after = stream(allCommits)
                    .skip(1) // drop first commit, which published the template
                    .filter(c -> modifiesTaskFiles(project, c))
                    .collect(toCollection(ArrayList::new));
            var before = splitBeforeAfter(after, lastId);

            stats.append(project.getName()).append("\t").append(before.size())
                    .append("\t").append(after.size()).append("\n");

            progress.advance();
        }
        progress.printSummary();

        System.out.println(stats);
    }

    private boolean modifiesTaskFiles(Project project, Commit c) {
        if (args.getTaskFiles() == null) {
            return true;
        }

        List<Diff> diffs;
        try {
            diffs = gitlab.getCommitsApi().getDiff(project.getId(), c.getId());
        } catch (GitLabApiException e) {
            throw new RuntimeException(e);
        }
        return diffs.stream()
                .map(Diff::getNewPath)
                .filter(path -> path.startsWith(args.getProjectDir() + "/"))
                .map(path -> path.substring(args.getProjectDir().length() + 1))
                .anyMatch(args.getTaskFiles()::contains);
    }

    public List<Commit> splitBeforeAfter(List<Commit> commits, String id) {
        var before = new ArrayList<Commit>();
        for (var i = commits.iterator(); i.hasNext();) {
            var commit = i.next();
            before.add(commit);
            i.remove();
            if (commit.getId().equals(id)) {
                break;
            }
        }
        return before;
    }

    interface Args extends Cmd.Args {
        @Option
        String getGroupName();

        @Option
        String getProjectDir();

        @Option(defaultToNull = true)
        List<String> getTaskFiles();

        @Option
        String getDate(); // yyyy-MM-dd-HH:mm
    }
}
