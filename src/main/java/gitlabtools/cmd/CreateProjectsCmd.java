package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.CommitPayload;
import org.gitlab4j.api.models.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static org.gitlab4j.api.models.CommitAction.Action.CREATE;

public class CreateProjectsCmd extends Cmd<CreateProjectsCmd.Args> {

    private final AccessLevel access;

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
        access = AccessLevel.valueOf(args.getDefaultBranchAccess().toUpperCase());
    }

    @Override
    protected void doExecute() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();

        var group = getGroup(args.getGroupName());
        var subgroup = getSubgroup(group, args.getSubgroupName());
        var existingProjects = gitlab.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());

        var teams = args.isTeamProjects()
                ? readTeamsCourseFile()
                : readSimpleCourseFile(); // teams are single people

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

            var project = gitlab.getProjectApi().createProject(subgroup.getId(), projectName);

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

            progress.advance();
        }
    }

    private List<Set<String>> readSimpleCourseFile() throws IOException {
        try (var lines = Files.lines(Path.of(args.getCourseFile()))) {
            return lines
                    .map(this::stripComment)
                    .map(String::strip)
                    .filter(not(String::isEmpty))
                    .map(this::normalizeUsername)
                    .map(Set::of)
                    .collect(toList());
        }
    }

    private Collection<? extends Set<String>> readTeamsCourseFile() throws IOException {
        try (var lines = Files.lines(Path.of(args.getCourseFile()))) {
            return lines
                    .map(this::stripComment)
                    .map(String::strip)
                    .filter(not(String::isEmpty))
                    .map(this::parseTeamsCourseLine)
                    .collect(groupingBy(m -> m.team,
                            mapping(m -> m.username, toCollection(TreeSet::new))))
                    .values();
        }
    }

    private TeamMember parseTeamsCourseLine(String line) {
        var parts = line.split("\t");
        if (parts.length != 2) {
            throw new RuntimeException("illegal line in course file: '" + line + "'");
        }
        return new TeamMember(normalizeUsername(parts[0]), parts[1]);
    }

    private String stripComment(String line) {
        return line.split("//")[0];
    }

    private String normalizeUsername(String raw) {
        var parts = raw.split("@");
        if (parts.length == 1) {
            return raw;
        }
        if (parts.length == 2) {
            // username is an email address; use only first part
            return parts[0];
        } else {
            throw new RuntimeException("invalid username in course file: " + raw);
        }
    }

    private static class TeamMember {
        String username;
        String team;

        public TeamMember(String username, String team) {
            this.username = username;
            this.team = team;
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
    }
}
