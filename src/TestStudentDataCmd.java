import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static org.apache.commons.csv.CSVFormat.TDF;

import java.nio.file.Paths;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public class TestStudentDataCmd extends CmdWithCourseData<TestStudentDataCmd.Args> {

    public TestStudentDataCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var groupStudents = new CsvReader(TDF.withHeader())
                .read(Paths.get(args.getGroupsFile()), GroupStudent.class);

        var complete = students.stream().filter(s -> s.username.isPresent()).count();
        System.out.printf("%d/%d students in course have a username.\n",
                complete, students.size());

        int matchedCourse = 0;
        for (var groupStudent : groupStudents) {
            var courseStudent = students.stream()
                    .filter(s -> s.legi.equals(groupStudent.legi))
                    .findFirst();
            if (courseStudent.isEmpty()) {
                System.err.println("No student found in course file for " + groupStudent.firstName +
                        " " + groupStudent.lastName + " (" + groupStudent.legi + ")");
            } else if (!groupStudent.username.equals(courseStudent.get().username.orElse(null))) {
                System.err.println("Different username for " + groupStudent.firstName + " " +
                        groupStudent.lastName + " (" + groupStudent.legi + "): " +
                        groupStudent.username + " vs. " + courseStudent.get().username.orElse(null));
            } else {
                matchedCourse++;
            }
        }
        System.out.printf("%d/%d students in groups matched with course file.\n",
                matchedCourse, groupStudents.size());

        int foundUser = 0;
        for (var student : students) {
            if (student.username.isPresent()) {
                var user = users().stream()
                        .filter(u -> u.getUsername().equals(student.username.get()))
                        .findFirst();
                if (user.isEmpty()) {
                    System.err.println("No Gitlab user found for " + student.firstName +
                        " " + student.lastName + " (" + student.legi + ", " + student.username.get() + ")");
                } else {
                    foundUser++;
                }
            }
        }
        System.out.printf("%d/%d students in course file have a Gitlab user.\n", foundUser, students.size());
    }

    public interface Args extends CmdWithCourseData.Args {
        @Option(defaultValue = "groups.txt") // tab-separated
        String getGroupsFile();
    }
}
