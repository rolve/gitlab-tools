package gitlabtools;

import static java.lang.String.format;
import static java.util.Optional.empty;

import java.util.Optional;

import csv.Column;

public class Student {
    @Column("Nummer")
    public String legi;
    @Column("Rufname")
    public String firstName;
    @Column("Familienname")
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
