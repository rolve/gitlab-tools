package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import csv.CsvReader;
import gitlabtools.Student;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Project;

import java.nio.file.Path;
import java.util.List;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.csv.CSVFormat.TDF;

public class CreateProjectsCmd extends Cmd<CreateProjectsCmd.Args> {

    private final List<Student> students;
    private final AccessLevel access;

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
        students = new CsvReader<>(TDF.withHeader(), Student.class)
                .readAll(Path.of(args.getCourseFile()));
        for (var student : students) {
            student.normalizeUsername();
        }
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

        System.out.println("Creating projects for " + students.size() + " "
                + (students.size() > 1 ? "people" : "person") + "...");
        for (var student : students) {
            var projectName = student.username;
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

    public interface Args extends gitlabtools.cmd.Args {
        @Option(defaultValue = "course.txt") // tab-separated
        String getCourseFile();

        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getDefaultBranchAccess();

        @Option(defaultToNull = true)
        String getProjectNamePrefix();
    }
}
