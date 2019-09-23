import csv.Column;

class GroupStudent {
    @Column("Nummer")
    String legi;
    @Column("Raumzeit")
    String room;
    @Column("Rufname")
    String firstName;
    @Column("Nachname")
    String lastName;
    @Column("NETHZ")
    String nethz;

    String commitHash;
}
