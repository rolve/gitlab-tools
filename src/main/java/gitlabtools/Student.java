package gitlabtools;

import csv.Column;

import java.io.IOException;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.empty;

public class Student {
    @Column("Vorname")
    public String firstName;
    @Column("Nachname")
    public String lastName;
    @Column("Username")
    public String username;

    public void normalizeUsername() throws IOException {
        var parts = this.username.split("@");
        if (parts.length == 2) {
            // username is an email address; use only first part
            this.username = parts[0];
        } else if (parts.length > 2) {
            throw new IOException("invalid username in course file: " + this.username);
        }
    }

    public String name() {
        return firstName + " " + lastName;
    }

    public String toString() {
        return format("%s (%s)", name(), username);
    }
}
