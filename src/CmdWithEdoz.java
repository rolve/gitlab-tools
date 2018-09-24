import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.gitlab4j.api.models.User;

import com.lexicalscope.jewel.cli.Option;

public abstract class CmdWithEdoz<A extends CmdWithEdoz.Args> extends Cmd<A> {
    
    protected final List<Student> students;

    public CmdWithEdoz(A args) throws Exception {
        super(args);
        students = readEdozFile(Paths.get(args.getEdozFile()));
        students.forEach(this::findNethzAndUser);
    }

    private static List<Student> readEdozFile(Path edozFile) throws IOException {
        return readAllLines(edozFile).stream()
                .skip(1)
                .map(line -> new Student(line.split("\t")))
                .collect(toList());
    }

    private void findNethzAndUser(Student student) {
        student.user = Optional.ofNullable(users.get(student.firstAndLastName));

        var mailParts = student.mail.split("@");
        if (mailParts[0].matches("[a-z]+") && mailParts[1].matches("(student\\.)?ethz\\.ch")) {
            student.nethz = Optional.of(mailParts[0]);
        } else {
            System.err.printf("Warning: unexpected email address %s\n", student.mail);
            student.nethz = student.user.map(User::getUsername);
            if (!student.nethz.isPresent()) {
                System.err.printf("Warning: no nethz name for %s (%s)\n",
                        student.firstAndLastName, student.legi);
            }
        }
    }

    static class Student {
        final String legi;
        final String firstAndLastName;
        final String mail;
        Optional<String> nethz = empty();
        Optional<User> user = empty();

        Student(String[] cells) {
            legi = cells[3];
            firstAndLastName = cells[2] + " " + cells[0];
            mail = cells[22];
        }

        public String toString() {
            return format("%s (%s)", firstAndLastName, nethz.orElse("???"));
        }
    }

    public interface Args extends Cmd.Args {
        @Option(defaultValue = {"edoz.txt"})
        String getEdozFile();
    }
}
