import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.apache.commons.csv.CSVFormat.TDF;

import java.nio.file.Paths;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public class TestStudentDataCmd extends CmdWithEdoz<TestStudentDataCmd.Args> {

    public TestStudentDataCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var groupStudents = new CsvReader(TDF.withHeader())
                .read(Paths.get(args.getGroupsFile()), GroupStudent.class);

        var complete = students.stream().filter(s -> s.nethz.isPresent()).count();
        System.out.printf("%d/%d students in eDoz have NETHZ.\n",
                complete, students.size());

        int matchedEdoz = 0;
        for (var groupStudent : groupStudents) {
            var edozStudent = students.stream()
                    .filter(s -> s.legi.equals(groupStudent.legi))
                    .findFirst();
            if (edozStudent.isEmpty()) {
                System.err.println("No eDoz student found for " + groupStudent.firstName +
                        " " + groupStudent.lastName + " (" + groupStudent.legi + ")");
            } else if (!groupStudent.nethz.equals(edozStudent.get().nethz.orElse(null))) {
                System.err.println("Different NETHZ for " + groupStudent.firstName + " " +
                        groupStudent.lastName + " (" + groupStudent.legi + "): " +
                        groupStudent.nethz + " vs. " + edozStudent.get().nethz.orElse(null));
            } else {
                matchedEdoz++;
            }
        }
        System.out.printf("%d/%d students in groups matched with eDoz.\n", matchedEdoz, groupStudents.size());

        int foundUser = 0;
        for (var student : students) {
            if (student.nethz.isPresent()) {
                var user = users().stream()
                        .filter(u -> u.getUsername().equals(student.nethz.get()))
                        .findFirst();
                if (user.isEmpty()) {
                    System.err.println("No Gitlab user found for " + student.firstName +
                        " " + student.lastName + " (" + student.legi + ")");
                } else {
                    foundUser++;
                }
            }
        }
        System.out.printf("%d/%d students in eDoz have a Gitlab user.\n", foundUser, students.size());
    }

    public interface Args extends CmdWithEdoz.Args {
        @Option(defaultValue = "groups.txt") // tab-separated
        String getGroupsFile();
    }
}
