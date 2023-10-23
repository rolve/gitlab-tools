package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.Project;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.time.LocalDate.now;
import static java.time.LocalDateTime.parse;
import static java.time.ZoneId.systemDefault;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.GERMAN;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toSet;

/**
 * Creates a merge request for each project in the given group, to be used for
 * code review. By default, the merge request is created so that all changes on
 * the default branch since the last commit of an instructor are included.
 */
public class CreateMergeRequestCmd extends CmdForProjects<CreateMergeRequestCmd.Args> {

    private final Instant deadline;
    private final Instant releaseDateTime;
    private final List<String> projectsWithNoCommits = new ArrayList<>();

    public CreateMergeRequestCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));

        LocalDateTime localDeadline;
        try {
            localDeadline = args.getDeadline() == null
                    ? LocalDateTime.now()
                    : parse(args.getDeadline());
            deadline = localDeadline.atZone(systemDefault()).toInstant();
            if (args.getDeadline() != null) {
                System.out.println("Using local deadline " + localDeadline + " (UTC: " + deadline + ")");
            }
        } catch (DateTimeParseException e) {
            throw new ArgumentValidationException("Invalid deadline: " + e.getParsedString(), e);
        }

        try {
            var localReleaseDateTime = args.getReleaseDateTime() == null
                    ? localDeadline
                    : parse(args.getReleaseDateTime());
            releaseDateTime = localReleaseDateTime.atZone(systemDefault()).toInstant();
            if (releaseDateTime.isAfter(deadline)) {
                throw new ArgumentValidationException("Release date/time must not be after deadline");
            }
            if (args.getReleaseDateTime() != null) {
                System.out.println("Using local release date/time " + localReleaseDateTime + " (UTC: " + releaseDateTime + ")");
            }
        } catch (DateTimeParseException e) {
            throw new ArgumentValidationException("Invalid release date/time: " + e.getParsedString(), e);
        }
    }

    @Override
    protected void executeTasks() throws Exception {
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

            var sourceCommit = lastPushedCommitBefore(project, project.getDefaultBranch(), deadline);
            var targetCommit = lastPushedCommitBefore(project, project.getDefaultBranch(),
                    releaseDateTime, e -> instructors.contains(e.getAuthorUsername()));
            if (sourceCommit == null || targetCommit == null) {
                progress.advance("failed");
                progress.interrupt();
                System.out.println("Source or target commit not found for project " + project.getName());
                continue;
            }
            if (sourceCommit.equals(targetCommit)) {
                progress.advance("failed");
                projectsWithNoCommits.add(project.getName());
                continue;
            }

            createProtectedBranch(project, sourceBranch, sourceCommit);
            createProtectedBranch(project, targetBranch, targetCommit);

            gitlab.getMergeRequestApi().createMergeRequest(project,
                    sourceBranch, targetBranch, title, args.getDescription(), null);

            progress.advance();
        }
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
        /**
         * To create a merge request, two branches are needed: a source branch
         * and a target branch. To be able to review a specific and fixed set of
         * commits, this command always creates two new branches to that end.
         * This option allows to specify the name of the source branch. If not
         * specified, a default name containing the current date is used. The
         * target branch is named like the source branch, but with the suffix
         * '-base'.
         */
        @Option(defaultToNull = true)
        String getBranchName();

        /**
         * By default, the source branch is created at the latest commit pushed
         * to the default branch to date. This option allows to specify an
         * earlier deadline. If specified, the source branch is instead created
         * at the last commit pushed to the default branch before the given
         * deadline. The deadline is interpreted as local time and must be
         * specified in ISO-8601 format, e.g. "2007-12-03T10:15:30".
         */
        @Option(defaultToNull = true)
        String getDeadline();

        /**
         * By default, the target branch is created at the last commit pushed to
         * the default branch by an instructor before the
         * {@linkplain #getDeadline() deadline}. This option allows to specify
         * an earlier "release" date/time. If specified, the target branch is
         * instead created at the last commit pushed to the default branch by an
         * instructor <em>before the given date/time</em>. It is interpreted as
         * local time and must be specified in ISO-8601 format, e.g.
         * "2007-12-03T10:15:30".
         */
        @Option(defaultToNull = true)
        String getReleaseDateTime();

        @Option(defaultToNull = true)
        String getTitle();

        @Option(defaultValue = "")
        String getDescription();
    }
}
