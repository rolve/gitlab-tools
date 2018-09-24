import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.toSet;

import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

public class CreateProjectsCmd extends CmdWithEdoz<CreateProjectsCmd.Args> {

    public CreateProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        System.out.printf("Creating projects for %d students...\n", students.size());

        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var existingProjects = gitlab.getGroupApi().getProjects(studGroup.getId()).stream()
                .map(Project::getName).collect(toSet());
        int created = 0;
        int existing = 0;
        for (var student : students) {
            if (student.nethz.isPresent()) {
                if (existingProjects.contains(student.nethz.get())) {
                    existing++;
                } else {
                    gitlab.getProjectApi().createProject(studGroup.getId(), student.nethz.get());
                    created++;
                }
            } else {
                System.err.printf("Warning: no project created for %s\n",
                        student.firstAndLastName);
            }
            if (created % 10 == 0 && created > 0) {
                System.out.printf("%d projects created\n", created);
            }
        }
        System.out.printf("Done. %d projects created, %d already existed.\n",
                created, existing);
    }

    public interface Args extends CmdWithEdoz.Args {
        @Option
        String getGroupName();
    }
}
