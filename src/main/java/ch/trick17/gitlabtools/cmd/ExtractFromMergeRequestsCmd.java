package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Project;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.lang.String.join;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.rangeClosed;

/**
 * Extracts data (e.g. points) from merge requests for each project in the
 * given group and prints it in TSV format, to be further processed in a
 * spreadsheet. The data is extracted based on regular expressions provided
 * using the {@code --patterns} option.
 * <p>
 * This command assumes that there are no merge requests with duplicate titles
 * in the same project. It also sort of assumes that the merge request titles
 * are generally the same across projects; otherwise, the table will be very
 * sparse.
 */
public class ExtractFromMergeRequestsCmd extends CmdForProjects<ExtractFromMergeRequestsCmd.Args> {

    private final List<Pattern> patterns;
    private final Map<Project, Map<MergeRequest, List<String>>> allMatches = new HashMap<>();
    private Set<String> instructors;

    public ExtractFromMergeRequestsCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
        if (args.getPatterns().isEmpty()) {
            throw new ArgumentValidationException("No patterns specified");
        }
        try {
            patterns = args.getPatterns().stream()
                    .map(Pattern::compile)
                    .collect(toList());
        } catch (PatternSyntaxException e) {
            throw new ArgumentValidationException("Invalid pattern " + e.getMessage());
        }
    }

    @Override
    protected void executeTasks() throws Exception {
        instructors = gitlab.getGroupApi().getMembers(getGroup()).stream()
                .map(Member::getUsername)
                .collect(toSet());

        for (var project : getProjects()) {
            allMatches.put(project, new HashMap<>());

            var mergeRequests = gitlab.getMergeRequestApi().getMergeRequests(project);
            for (var req : mergeRequests) {
                var discussions = gitlab.getDiscussionsApi()
                        .getMergeRequestDiscussions(project, req.getIid());
                for (var discussion : discussions) {
                    for (var note : discussion.getNotes()) {
                        if (!note.getSystem() && include(note.getAuthor().getUsername())) {
                            extractMatches(project, req, note.getBody());
                        }
                    }
                }
            }
            progress.advance();
        }
    }

    private boolean include(String username) {
        return args.isIncludeNonInstructors() || instructors.contains(username);
    }

    private void extractMatches(Project project, MergeRequest req, String text) {
        for (var pattern : patterns) {
            var matches = pattern.matcher(text).results()
                    .flatMap(r -> allMatches(r))
                    .collect(toList());
            if (!matches.isEmpty()) {
                allMatches.get(project)
                        .computeIfAbsent(req, k -> new ArrayList<>())
                        .addAll(matches);
            }
        }
    }

    private static Stream<String> allMatches(MatchResult r) {
        var startGroup = r.groupCount() == 0 ? 0 : 1;
        return rangeClosed(startGroup, r.groupCount()).mapToObj(r::group);
    }

    @Override
    protected void printSummary() {
        super.printSummary();
        System.out.println();

        var mergeRequestTitles = allMatches.values().stream()
                .flatMap(m -> m.keySet().stream())
                .sorted(comparing(MergeRequest::getCreatedAt))
                .map(MergeRequest::getTitle)
                .distinct()
                .collect(toList());

        System.out.print("Project");
        mergeRequestTitles.forEach(r -> System.out.print("\t" + r));
        System.out.println();

        var projects = allMatches.keySet().stream()
                .sorted(comparing(Project::getName))
                .collect(toList());
        for (var project : projects) {
            System.out.print(project.getName());
            for (var req : mergeRequestTitles) {
                var matches = allMatches.get(project).entrySet().stream()
                        .filter(e -> e.getKey().getTitle().equals(req))
                        .findFirst()
                        .map(Entry::getValue)
                        .orElse(emptyList());
                System.out.print("\t" + join(", ", matches));
            }
            System.out.println();
        }
    }

    public interface Args extends CmdForProjects.Args {
        /**
         * A list of regular expressions to search for in the merge requests.
         * If a pattern contains capturing groups, the contents of the groups
         * will be extracted; otherwise, the entire match will be used.
         * If multiple matches are found for a single merge request, they are
         * joined with a comma.
         */
        @Option
        List<String> getPatterns();

        /**
         * By default, only comments by instructors are considered. If this
         * option is set, comments by non-instructors are also included.
         */
        @Option
        boolean isIncludeNonInstructors();
    }
}
