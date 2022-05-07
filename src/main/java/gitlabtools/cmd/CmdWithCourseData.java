package gitlabtools.cmd;

import static org.apache.commons.csv.CSVFormat.TDF;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import csv.CsvReader;
import gitlabtools.Student;

public abstract class CmdWithCourseData<A extends ArgsWithCourseData> extends Cmd<A> {

    protected final List<Student> students;
    private Map<String, String> specialUsers = null;

    public CmdWithCourseData(A args) throws IOException {
        super(args);
        students = new CsvReader<>(TDF.withHeader(), Student.class)
                .readAll(Path.of(args.getCourseFile()));
        students.forEach(this::findUsername);
    }

    private void findUsername(Student student) {
        var mailParts = student.mail.split("@");
        if (mailParts.length != 2) {
            throw new AssertionError("invalid email: " + student.mail);
        }
        if (mailParts[0].matches(args.getLocalPartPattern())
                && mailParts[1].matches(args.getDomainPattern())) {
            // first, try to infer username from email address
            student.username = Optional.of(mailParts[0]);
        } else if (nameToUserMap().containsKey(student.name())) {
            // then, try to find a matching Gitlab user and take the username from there
            student.username = Optional.of(nameToUserMap().get(student.name()).getUsername());
        } else {
            // finally, look them up in the special users file
            if (specialUsers == null) {
                loadSpecialUsers();
            }
            student.username = Optional.ofNullable(specialUsers.get(student.mail));
        }

        // if we still haven't got anything, warn
        if (student.username.isEmpty()) {
            System.err.printf("Warning: no username for %s (%s)\n",
                    student.name(), student.mail);
        }
    }

    private void loadSpecialUsers() {
        specialUsers = new HashMap<>();
        try (var lines = Files.lines(Paths.get(args.getSpecialUsersFile()))) {
            for (var pair : iterable(lines.map(l -> l.split("\t")))) {
                if (pair.length != 2) {
                    throw new AssertionError("invalid file format");
                }
                specialUsers.put(pair[0], pair[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException("could not load " + args.getSpecialUsersFile(), e);
        }
    }
}
