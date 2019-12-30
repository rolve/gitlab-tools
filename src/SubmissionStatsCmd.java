import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.gitlab4j.api.Constants.ActionType.PUSHED;
import static org.gitlab4j.api.Constants.SortOrder.DESC;

import java.text.SimpleDateFormat;
import java.util.*;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;

import com.lexicalscope.jewel.cli.Option;

public class SubmissionStatsCmd extends Cmd<SubmissionStatsCmd.Args> {

    private static final Cache<List<Event>> eventCache = new Cache<>();
    private static final Cache<Map<String, Commit>> commitCache = new Cache<>();
    private static final Cache<List<Diff>> diffCache = new Cache<>();

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

            var commits = commitCache.update(new Cache.Key(args.getGitlabUrl(), id),
                    () -> stream(gitlab.getCommitsApi().getCommits(id, 100))
                            .collect(toMap(Commit::getId, identity())));
            var last = commits.get(lastPush.get().getPushData().getCommitTo());

            var before = commits.values().stream()
                    .filter(c -> isAncestorOf(c, last, commits))
                    .collect(toList());
            var after = commits.values().stream()
                    .filter(not(before::contains))
                    .collect(toList());
            var beforeCount = before.stream()
                    .map(commit -> getDiff(project, commit))
                    .filter(diff -> modifiesProjectDir(diff))
                    .skip(1) // drop first commit, which published the template
                    .filter(diff -> modifiesTaskFiles(diff))
                    .count();
            var afterCount = after.stream()
                    .map(commit -> getDiff(project, commit))
                    .filter(diff -> modifiesTaskFiles(diff))
                    .count();

            stats.append(project.getName()).append("\t").append(beforeCount)
                    .append("\t").append(afterCount).append("\n");

            progress.advance();
        }
        progress.printSummary();

        System.out.println(stats);
    }

    private static boolean isAncestorOf(Commit c, Commit other, Map<String, Commit> commits) {
        return c == other || other.getParentIds().stream()
                .map(commits::get)
                .filter(Objects::nonNull)
                .anyMatch(parent -> isAncestorOf(c, parent, commits));
    }

    private List<Diff> getDiff(Project project, Commit c) {
        try {
            var key = new Cache.Key(args.getGitlabUrl(), project.getId() + "#" + c.getId());
            return diffCache.update(key, () ->
                    gitlab.getCommitsApi().getDiff(project.getId(), c.getId()));
        } catch (GitLabApiException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean modifiesProjectDir(List<Diff> diff) {
        return diff.stream()
                .map(Diff::getNewPath)
                .anyMatch(path -> path.startsWith(args.getProjectDir() + "/"));
    }

    private boolean modifiesTaskFiles(List<Diff> diff) {
        if (args.getTaskFiles() == null) {
            return true;
        }
        return diff.stream()
                .map(Diff::getNewPath)
                .filter(path -> path.startsWith(args.getProjectDir() + "/"))
                .map(path -> path.substring(args.getProjectDir().length() + 1))
                .anyMatch(args.getTaskFiles()::contains);
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
