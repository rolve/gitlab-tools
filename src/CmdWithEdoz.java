import static org.apache.commons.csv.CSVFormat.TDF;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.gitlab4j.api.models.User;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public abstract class CmdWithEdoz<A extends CmdWithEdoz.Args> extends Cmd<A> {

    protected final List<EdozStudent> students;

    public CmdWithEdoz(A args) throws Exception {
        super(args);
        students = new CsvReader(TDF.withHeader()).read(Paths.get(args.getEdozFile()), EdozStudent.class);
        students.forEach(this::findNethzAndUser);
    }

    private void findNethzAndUser(EdozStudent student) {
        student.user = Optional.ofNullable(nameToUserMap().get(student.name()));

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

    public interface Args extends Cmd.Args {
        @Option(defaultValue = "edoz.txt")
        String getEdozFile();
    }
}
