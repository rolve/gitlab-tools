package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.*;

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
        var existingProjects = getProjectsIn(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());

        List<String> projects;
        if (args.isTeamProjects()) {
            projects = readTeamsCourseFile();
        } else {
            projects = readSimpleCourseFile();
        }

        System.out.println("Creating " + projects.size() + " project(s)...");
        for (var projectName : projects) {
            if (args.getProjectNamePrefix() != null) {
                if (args.getProjectNamePrefix().contains("_")) {
                    throw new AssertionError("illegal prefix; must not contain _");
                }
                projectName = args.getProjectNamePrefix() + "_" + projectName;
            }
            if (existingProjects.contains(projectName)) {
                progress.advance("existing");
            } else {
                var project = gitlab.getProjectApi().createProject(subgroup.getId(), projectName);

                // remove all protected branches first
                var branches = branchApi.getProtectedBranches(project.getId());
                for (var branch : branches) {
                    branchApi.unprotectBranch(project.getId(), branch.getName());
                }

                // then configure default branch so that users with configured role
                // ('developer' by default) can push & merge, but not force-push
                branchApi.protectBranch(project.getId(), args.getDefaultBranch(), access, access);

                progress.advance();
            }
        }
    }

    private List<String> readSimpleCourseFile() throws IOException {
        try (var lines = Files.lines(Path.of(args.getCourseFile()))) {
            return lines
                    .map(this::normalizeUsername)
                    .collect(toList());
        }
    }

    private List<String> readTeamsCourseFile() throws IOException {
        try (var lines = Files.lines(Path.of(args.getCourseFile()))) {
            return lines
                    .map(s -> s.split("\t"))
                    .peek(parts -> parts[0] = normalizeUsername(parts[0]))
                    .collect(groupingBy(parts -> parts[1],
                            mapping(parts -> parts[0], toCollection(TreeSet::new))))
                    .values().stream()
                    .map(set -> String.join("_", set))
                    .collect(toList());
        }
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

    public interface Args extends gitlabtools.cmd.Args {
        @Option
        boolean isTeamProjects();

        @Option(defaultValue = "course.txt") // one username or email address per line
        String getCourseFile();

        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getDefaultBranchAccess();

        @Option(defaultToNull = true)
        String getProjectNamePrefix();
    }
}
