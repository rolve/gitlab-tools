import static org.apache.commons.csv.CSVFormat.TDF;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public abstract class CmdWithCourseData<A extends CmdWithCourseData.Args> extends Cmd<A> {

    protected final List<Student> students;
    private Map<String, String> specialUsernames = null;

    public CmdWithCourseData(A args) throws Exception {
        super(args);
        students = new CsvReader(TDF.withHeader()).read(Paths.get(args.getCourseFile()), Student.class);
        students.forEach(this::findUsername);
    }

    private void findUsername(Student student) {
        var mailParts = student.mail.split("@");
        if (mailParts[0].matches("[a-z]+") && mailParts[1].matches("(student\\.)?ethz\\.ch")) {
            // first, try to infer username from email address
            student.username = Optional.of(mailParts[0]);
        } else if (nameToUserMap().containsKey(student.name())) {
            // then, try to find a matching Gitlab user and take the username from there
            student.username = Optional.of(nameToUserMap().get(student.name()).getUsername());
        } else {
            // finally, look them up in the special username file
            if (specialUsernames == null) {
                loadSpecialUsernames();
            }
            student.username = Optional.ofNullable(specialUsernames.get(student.legi));
        }

        // if we still haven't got anything, warn
        if (!student.username.isPresent()) {
            System.err.printf("Warning: no username for %s (%s)\n",
                    student.name(), student.legi);
        }
    }

    private void loadSpecialUsernames() {
        specialUsernames = new HashMap<>();
        try (var lines = Files.lines(Paths.get(args.getSpecialUsernameFile()))) {
            for (var pair : iterable(lines.map(l -> l.split("\t")))) {
                if (pair.length != 2) {
                    throw new AssertionError("invalid file format");
                }
                specialUsernames.put(pair[0], pair[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException("could not load " + args.getSpecialUsernameFile(), e);
        }
    }

    public interface Args extends Cmd.Args {
        @Option(defaultValue = "edoz.txt") // tab-separated
        String getCourseFile();

        @Option(defaultValue = "specialnethz.txt") // tab-separated file with legi & username
        String getSpecialUsernameFile();
    }
}