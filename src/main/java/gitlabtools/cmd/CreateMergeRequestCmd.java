package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.time.LocalDate.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.GERMAN;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toSet;
import static org.gitlab4j.api.Constants.ActionType.PUSHED;
import static org.gitlab4j.api.Constants.SortOrder.DESC;

public class CreateMergeRequestCmd extends CmdForProjects<CreateMergeRequestCmd.Args> {

    private final List<String> projectsWithNoCommits = new ArrayList<>();

    public CreateMergeRequestCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var sourceBranch = requireNonNullElse(args.getBranchName(), "review-" + now());
        var targetBranch = sourceBranch + "-base";
        var title = requireNonNullElse(args.getTitle(),
                "Code-Review für die Abgabe vom " + now().format(ofPattern("dd. MMMM uuuu", GERMAN)));

        var instructors = gitlab.getGroupApi().getMembers(getGroup()).stream()
                .map(Member::getUsername)
                .collect(toSet());

        for (var project : getProjects()) {
            if (gitlab.getRepositoryApi().getOptionalBranch(project, targetBranch).isPresent()) {
                progress.advance("existing");
                continue;
            }

            var pushes = gitlab.getEventsApi().getProjectEvents(project, PUSHED, null, null, null, DESC);
            var lastInstructorCommit = lastPushedCommit(
                    pushes.stream().filter(e -> instructors.contains(e.getAuthorUsername())),
                    project.getDefaultBranch());
            var lastCommit = lastPushedCommit(pushes.stream(), project.getDefaultBranch());
            if (lastCommit.equals(lastInstructorCommit)) {
                progress.advance("no commits");
                projectsWithNoCommits.add(project.getName());
                continue;
            }

            createProtectedBranch(project, sourceBranch, lastCommit);
            createProtectedBranch(project, targetBranch, lastInstructorCommit);

            gitlab.getMergeRequestApi().createMergeRequest(project,
                    sourceBranch, targetBranch, title, args.getDescription(), null);

            progress.advance();
        }
    }

    private String lastPushedCommit(Stream<Event> pushes, String branch) {
        return pushes
                .map(Event::getPushData)
                .filter(p -> branch.equals(p.getRef()))
                .map(PushData::getCommitTo)
                .filter(Objects::nonNull)
                .findFirst().orElseThrow();
    }

    private void createProtectedBranch(Project project, String name, String ref) throws GitLabApiException {
        gitlab.getRepositoryApi().createBranch(project, name, ref);
        gitlab.getProtectedBranchesApi().protectBranch(project, name,
                AccessLevel.MAINTAINER, AccessLevel.MAINTAINER);
    }

    @Override
    protected void printSummary() {
        super.printSummary();
        if (!projectsWithNoCommits.isEmpty()) {
            System.out.println("\nProjects without commits:");
            for (var project : projectsWithNoCommits) {
                System.out.println("    " + project);
            }
        }
    }

    public interface Args extends CmdForProjects.Args {
        @Option(defaultToNull = true)
        String getBranchName();

        @Option(defaultToNull = true)
        String getTitle();

        @Option(defaultValue = "")
        String getDescription();
    }
}
