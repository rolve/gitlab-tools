package gitlabtools;

import csv.Column;

import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.empty;

public class Student {
    @Column("Vorname")
    public String firstName;
    @Column("Nachname")
    public String lastName;
    @Column("E-Mail")
    public String mail;

    public Optional<String> username = empty();

    public String name() {
        return firstName + " " + lastName;
    }

    public String toString() {
        return format("%s (%s)", name(), username.orElse("???"));
    }
}
