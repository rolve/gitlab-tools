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
        System.out.printf("We have the NETHZ for %d/%d students\n",
                complete, students.size());

        int matchedEdoz = 0;
        int foundUser = 0;
        for (var groupStudent : groupStudents) {
            var edozStudent = students.stream()
                    .filter(s -> s.legi.equals(groupStudent.legi))
                    .findFirst();
            if (edozStudent.isEmpty()) {
                System.err.println("No eDoz student found for " + groupStudent.firstName +
                        " " + groupStudent.lastName + " (" + groupStudent.legi + ")");
            } else if (edozStudent.get().nethz.equals(groupStudent.nethz)) {
                System.err.println("Different NETHZ for " + groupStudent.firstName + " " +
                        " " + groupStudent.lastName + " (" + groupStudent.legi + "):" +
                        groupStudent.nethz + " vs. " + edozStudent.get().nethz);
            } else {
                matchedEdoz++;
            }

            if (groupStudent.nethz.isBlank()) {
                System.err.println("Student " + groupStudent.firstName + " " +
                        groupStudent.lastName + " (" + groupStudent.legi + ") has no NETHZ");
            } else {
                var user = users().stream()
                        .filter(u -> u.getUsername().equals(groupStudent.nethz))
                        .findFirst();
                if (user.isEmpty()) {
                    System.err.println("No Gitlab user found for " + groupStudent.firstName +
                        " " + groupStudent.lastName + " (" + groupStudent.legi + ")");
                } else {
                    foundUser++;
                }
            }
        }
        System.out.printf("%d/%d students in groups matched with eDoz.\n", matchedEdoz, groupStudents.size());
        System.out.printf("%d/%d students in groups have a Gitlab user.\n", foundUser, groupStudents.size());
    }

    public interface Args extends CmdWithEdoz.Args {
        @Option(defaultValue = "groups.txt")    // comma-separated, make sure contains
        String getGroupsFile();                 // only @student.ethz.ch addresses!
    }
}
