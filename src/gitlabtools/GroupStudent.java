package gitlabtools;

import csv.Column;

public class GroupStudent {
    @Column("Nummer")
    public String legi;
    @Column("Raumzeit")
    public String room;
    @Column("Rufname")
    public String firstName;
    @Column("Nachname")
    public String lastName;
    @Column("NETHZ")
    public String username;

    public String commitHash;
}
