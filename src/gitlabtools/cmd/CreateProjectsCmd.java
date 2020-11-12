package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toSet;

import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class CreateProjectsCmd extends CmdWithCourseData<CreateProjectsCmd.Args> {

    private final AccessLevel access;

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
        access = AccessLevel.valueOf(args.getMasterBranchAccess().toUpperCase());
    }

    @Override
    protected void doExecute() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();

        var group = getGroup(args.getGroupName());
        var subgroup = getSubgroup(group, args.getSubgroupName());
        var existingProjects = getProjectsIn(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());

        System.out.println("Creating projects for " + students.size() + " students...");
        for (var student : students) {
            if (student.username.isPresent()) {
                if (existingProjects.contains(student.username.get())) {
                    progress.advance("existing");
                } else {
                    var project = gitlab.getProjectApi().createProject(subgroup.getId(), student.username.get());

                    // remove all protected branches first
                    var branches = branchApi.getProtectedBranches(project.getId());
                    for (var branch : branches) {
                        branchApi.unprotectBranch(project.getId(), branch.getName());
                    }

                    // then protect 'master' from force-pushing
                    branchApi.protectBranch(project.getId(), "master", access, access);

                    progress.advance();
                }
            } else {
                progress.advance("failed");
                progress.interrupt();
                System.out.printf("Warning: no project created for %s\n", student.name());
            }
        }
    }

    public interface Args extends ArgsWithCourseData, ArgsWithProjectAccess {
        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getMasterBranchAccess();
    }
}
