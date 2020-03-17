package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toSet;

import org.gitlab4j.api.models.Project;

public class CreateProjectsCmd extends CmdWithCourseData<CreateProjectsCmd.Args> {

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, args.getSubgroupName());
        var existingProjects = getProjectsIn(studGroup).stream()
                .map(Project::getName).collect(toSet());

        System.out.println("Creating projects for " + students.size() + " students...");
        for (var student : students) {
            if (student.username.isPresent()) {
                if (existingProjects.contains(student.username.get())) {
                    progress.advance("existing");
                } else {
                    gitlab.getProjectApi().createProject(studGroup.getId(), student.username.get());
                    progress.advance();
                }
            } else {
                progress.advance("failed");
                progress.interrupt();
                System.out.printf("Warning: no project created for %s\n", student.name());
            }
        }
    }

    public interface Args extends ArgsWithCourseData, ArgsWithProjectAccess {}
}
