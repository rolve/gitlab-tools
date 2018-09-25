import static csv.CsvReader.Separator.TAB;
import static java.lang.String.format;
import static java.util.Optional.empty;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.gitlab4j.api.models.User;

import com.lexicalscope.jewel.cli.Option;

import csv.Column;
import csv.CsvReader;

public abstract class CmdWithEdoz<A extends CmdWithEdoz.Args> extends Cmd<A> {
    
    protected final List<Student> students;

    public CmdWithEdoz(A args) throws Exception {
        super(args);
        students = new CsvReader(TAB).read(Paths.get(args.getEdozFile()), Student.class);
        students.forEach(this::findNethzAndUser);
    }

    private void findNethzAndUser(Student student) {
        student.user = Optional.ofNullable(nameToUserMap.get(student.name()));

        var mailParts = student.mail.split("@");
        if (mailParts[0].matches("[a-z]+") && mailParts[1].matches("(student\\.)?ethz\\.ch")) {
            student.nethz = Optional.of(mailParts[0]);
        } else {
            System.err.printf("Warning: unexpected email address %s\n", student.mail);
            student.nethz = student.user.map(User::getUsername);
            if (!student.nethz.isPresent()) {
                System.err.printf("Warning: no nethz name for %s (%s)\n",
                        student.name(), student.legi);
            }
        }
    }

    static class Student {
        @Column("Nummer") String legi;
        @Column("Rufname") String firstName;
        @Column("Familienname") String lastName;
        @Column("E-Mail") String mail;

        Optional<String> nethz = empty();
        Optional<User> user = empty();

        public String name() {
            return firstName + " " + lastName;
        }

        public String toString() {
            return format("%s (%s)", name(), nethz.orElse("???"));
        }
    }

    public interface Args extends Cmd.Args {
        @Option(defaultValue = {"edoz.txt"})
        String getEdozFile();
    }
}
