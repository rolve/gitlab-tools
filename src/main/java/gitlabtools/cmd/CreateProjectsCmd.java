package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.CommitPayload;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProjectApprovalsConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static gitlabtools.CourseFileReader.readSimpleCourseFile;
import static gitlabtools.CourseFileReader.readTeamsCourseFile;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.gitlab4j.api.models.CommitAction.Action.CREATE;

public class CreateProjectsCmd extends Cmd<CreateProjectsCmd.Args> {

    private final AccessLevel access;
    private final Collection<? extends Set<String>> teams;

    public CreateProjectsCmd(String[] rawArgs) throws IOException {
        super(createCli(Args.class).parseArguments(rawArgs));
        access = AccessLevel.valueOf(args.getDefaultBranchAccess().toUpperCase());

        var courseFile = Path.of(args.getCourseFile()).toAbsolutePath();
        if (!exists(courseFile)) {
            throw new ArgumentValidationException("File " + courseFile + " not found");
        } else if (!isRegularFile(courseFile)) {
            throw new ArgumentValidationException("File " + courseFile + " is not a regular file");
        }
        teams = args.isTeamProjects()
                ? readTeamsCourseFile(courseFile)
                : readSimpleCourseFile(courseFile).stream()
                    .map(Set::of)
                    .collect(toList());
    }

    @Override
    protected void doExecute() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();

        var existingProjects = gitlab.getGroupApi().getProjects(args.getGroup()).stream()
                .map(Project::getName)
                .collect(toSet());

        System.out.println("Creating " + teams.size() + " project(s)...");
        for (var team : teams) {
            var projectName = String.join("_", team);
            if (args.getProjectNamePrefix() != null) {
                if (args.getProjectNamePrefix().contains("_")) {
                    throw new AssertionError("illegal prefix; must not contain _");
                }
                projectName = args.getProjectNamePrefix() + "_" + projectName;
            }
            if (existingProjects.contains(projectName)) {
                progress.advance("existing");
                continue;
            }

            var project = gitlab.getProjectApi().createProject(getGroup().getId(), projectName);

            // remove all protected branches first
            var branches = branchApi.getProtectedBranches(project);
            for (var branch : branches) {
                branchApi.unprotectBranch(project, branch.getName());
            }

            // then configure default branch so that users with configured role
            // ('developer' by default) can push & merge, but not force-push
            branchApi.protectBranch(project, args.getDefaultBranch(), access, access);

            // create initial commit in order to set default branch
            var text = args.getReadmeText() + String.join(", ", team);
            gitlab.getCommitsApi().createCommit(project, new CommitPayload()
                    .withCommitMessage("Initialize")
                    .withBranch(args.getDefaultBranch())
                    .withAction(CREATE, text, "README.md"));

            // configure some simplifying settings
            if (!args.isSkipSettings()) {
                var approvals = new ProjectApprovalsConfig()
                        .withMergeRequestsAuthorApproval(false)
                        .withMergeRequestsDisableCommittersApproval(true)
                        .withDisableOverridingApproversPerMergeRequest(true);
                try {
                    gitlab.getProjectApi().setApprovalsConfiguration(project, approvals);
                } catch (GitLabApiException e) {
                    progress.advance("successful (settings not supported)");
                    continue;
                }
            }

            progress.advance();
        }
    }

    public interface Args extends gitlabtools.cmd.Args {
        @Option
        boolean isTeamProjects();

        @Option(defaultValue = "course.txt") // one username or email address per line
        String getCourseFile();

        @Option(defaultValue = "main")
        String getDefaultBranch();

        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getDefaultBranchAccess();

        @Option(defaultToNull = true)
        String getProjectNamePrefix();

        @Option(defaultValue = "Privates Repository von ")
        String getReadmeText();

        @Option
        boolean isSkipSettings();
    }
}
