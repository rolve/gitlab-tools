package gitlabtools.cmd;

import csv.CsvReader;
import gitlabtools.Student;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.apache.commons.csv.CSVFormat.TDF;

public abstract class CmdWithCourseData<A extends ArgsWithCourseData> extends Cmd<A> {

    protected final List<Student> students;

    public CmdWithCourseData(A args) throws IOException {
        super(args);
        students = new CsvReader<>(TDF.withHeader(), Student.class)
                .readAll(Path.of(args.getCourseFile()));
        for (var student : students) {
            normalizeUsername(student);
        }
    }

    private void normalizeUsername(Student student) throws IOException {
        var parts = student.username.split("@");
        if (parts.length == 2) {
            // username is an email address; use only first part
            student.username = parts[0];
        } else if (parts.length > 2) {
            throw new IOException("invalid username in course file: " + student.username);
        }
    }
}
