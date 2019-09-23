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

public abstract class CmdWithEdoz<A extends CmdWithEdoz.Args> extends Cmd<A> {

    protected final List<EdozStudent> students;
    private Map<String, String> specialNethz = null;

    public CmdWithEdoz(A args) throws Exception {
        super(args);
        students = new CsvReader(TDF.withHeader()).read(Paths.get(args.getEdozFile()), EdozStudent.class);
        students.forEach(this::findNethz);
    }

    private void findNethz(EdozStudent student) {
        var mailParts = student.mail.split("@");
        if (mailParts[0].matches("[a-z]+") && mailParts[1].matches("(student\\.)?ethz\\.ch")) {
            // first, try to infer NETHZ from email address
            student.nethz = Optional.of(mailParts[0]);
        } else if (nameToUserMap().containsKey(student.name())) {
            // then, try to find a matching Gitlab user and take the NETHZ from there
            student.nethz = Optional.of(nameToUserMap().get(student.name()).getUsername());
        } else {
            // finally, look them up in the special NETHZ file
            if (specialNethz == null) {
                loadSpecialNethz();
            }
            student.nethz = Optional.ofNullable(specialNethz.get(student.legi));
        }

        // if we still haven't got anything, warn
        if (!student.nethz.isPresent()) {
            System.err.printf("Warning: no nethz name for %s (%s)\n",
                    student.name(), student.legi);
        }
    }

    private void loadSpecialNethz() {
        specialNethz = new HashMap<>();
        try (var lines = Files.lines(Paths.get(args.getSpecialNethzFile()))) {
            for (var pair : iterable(lines.map(l -> l.split("\t")))) {
                if (pair.length != 2) {
                    throw new AssertionError("invalid file format");
                }
                specialNethz.put(pair[0], pair[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException("could not load " + args.getSpecialNethzFile(), e);
        }
    }

    public interface Args extends Cmd.Args {
        @Option(defaultValue = "edoz.txt")
        String getEdozFile();

        @Option(defaultValue = "specialnethz.txt") // tab-separated file with legi + NETHZ
        String getSpecialNethzFile();
    }
}
