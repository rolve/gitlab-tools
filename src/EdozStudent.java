import static java.lang.String.format;
import static java.util.Optional.empty;

import java.util.Optional;

import csv.Column;

class EdozStudent {
    @Column("Nummer") String legi;
    @Column("Rufname") String firstName;
    @Column("Familienname") String lastName;
    @Column("E-Mail") String mail;

    Optional<String> nethz = empty();

    public String name() {
        return firstName + " " + lastName;
    }

    public String toString() {
        return format("%s (%s)", name(), nethz.orElse("???"));
    }
}