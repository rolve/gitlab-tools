package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Project;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toSet;

public class CreateProjectsCmd extends CmdWithCourseData<CreateProjectsCmd.Args> {

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

    public interface Args extends ArgsWithCourseData {
        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getDefaultBranchAccess();

        @Option(defaultToNull = true)
        String getProjectNamePrefix();
    }
}
