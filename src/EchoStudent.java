import csv.Column;

class EchoStudent {
    @Column("Raumzeit")
    String room;
    @Column("Rufname")
    String firstName;
    @Column("Nachname")
    String lastName;
    @Column("E-Mail")
    String email;

    String commitHash;

    String nethz() { return email.split("@")[0]; }
}