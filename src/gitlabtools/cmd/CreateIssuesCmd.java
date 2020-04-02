package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.apache.commons.csv.CSVFormat.TDF;

import java.nio.file.Path;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;
import gitlabtools.GroupStudent;

public class CreateIssuesCmd extends CmdWithCourseData<CreateIssuesCmd.Args> {

    public CreateIssuesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var group = getGroup(args.getGroupName());
        var subgroup = getSubgroup(group, args.getIssuesSubgroupName());
        var projectId = getProject(subgroup, args.getIssuesProjectName()).getId();

        var groupStudents = new CsvReader(TDF.withHeader())
                .readAll(Path.of(args.getGroupsFile()), GroupStudent.class);

        System.out.println("Creating issues for " + students.size() + " students...");
        var issues = gitlab.getIssuesApi();
        for (int s = 0; s < students.size(); s++) {
            var student = students.get(s);
            var groupStudent = groupStudents.stream()
                    .filter(e -> e.legi.equals(student.legi))
                    .findFirst();

            var description = String.format(
                    "Legi-Nummer: %s\n\n" +
                    "E-Mail-Adresse: %s\n\n" +
                    "Repository: https://gitlab.inf.ethz.ch/%s/students/%s",
                    student.legi, student.mail, args.getGroupName(), student.username.orElse("???"));
            var label = groupStudent.map(e -> e.room).orElse("Without group");

            issues.createIssue(projectId, student.toString(), description,
                    null, null, null, label, null, null, null, null);
            progress.advance();
        }
    }

    public interface Args extends ArgsWithCourseData {
        @Option
        String getGroupName();

        @Option(defaultValue = "material")
        String getIssuesSubgroupName();

        @Option(defaultValue = "student-issues")
        String getIssuesProjectName();

        @Option(defaultValue = "groups.txt") // tab-separated
        String getGroupsFile();
    }
}
