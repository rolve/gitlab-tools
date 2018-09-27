import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.apache.commons.csv.CSVFormat.EXCEL;

import java.nio.file.Paths;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public class CreateIssuesCmd extends CmdWithEdoz<CreateIssuesCmd.Args> {

    public CreateIssuesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var matGroup = getSubGroup(mainGroup, "material");
        var projectId = getProject(matGroup, "student-issues").getId();

        var echoStudents = new CsvReader(EXCEL.withHeader())
                .read(Paths.get(args.getGroupsFile()), EchoStudent.class);

        System.out.printf("Creating issues for %d students...\n", students.size());
        var issues = gitlab.getIssuesApi();
        for (int s = 0; s < students.size(); s++) {
            var student = students.get(s);
            var echoStudent = echoStudents.stream()
                    .filter(e -> e.legi.equals(student.legi))
                    .findFirst();

            var description = String.format(
                    "Legi-Nummer: %s\n\n" +
                    "E-Mail-Adresse: %s\n\n" +
                    "Repository: https://gitlab.inf.ethz.ch/%s/students/%s",
                    student.legi, student.mail, args.getGroupName(), student.nethz.orElse("???"));
            var label = echoStudent.map(e -> e.room).orElse("Without group");

            issues.createIssue(projectId, student.toString(), description,
                    null, null, null, label, null, null, null, null);

            if (s % 10 == 9) {
                System.out.printf("%d issues created\n", s + 1);
            }
        }
        System.out.println("Done.");
    }

    public interface Args extends CmdWithEdoz.Args {
        @Option
        String getGroupName();

        @Option(defaultValue = "groups.txt")    // comma-separated, make sure contains
        String getGroupsFile();                 // only @student.ethz.ch addresses!
    }
}
