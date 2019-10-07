import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toSet;

import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class CreateProjectsCmd extends CmdWithCourseData<CreateProjectsCmd.Args> {

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        System.out.printf("Creating projects for %d students...\n", students.size());

        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var existingProjects = getProjectsIn(studGroup).stream()
                .map(Project::getName).collect(toSet());
        int created = 0;
        int existing = 0;
        for (var student : students) {
            if (student.username.isPresent()) {
                if (existingProjects.contains(student.username.get())) {
                    existing++;
                } else {
                    gitlab.getProjectApi().createProject(studGroup.getId(), student.username.get());
                    created++;
                }
            } else {
                System.err.printf("Warning: no project created for %s\n", student.name());
            }
            if (created % 10 == 0 && created > 0) {
                System.out.printf("%d projects created\n", created);
            }
        }
        System.out.printf("Done. %d projects created, %d already exist.\n", created, existing);
    }

    public interface Args extends CmdWithCourseData.Args {
        @Option
        String getGroupName();
    }
}
