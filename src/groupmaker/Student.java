package groupmaker;

import static groupmaker.GroupMaker.TUE;
import static groupmaker.GroupMaker.WED;
import static groupmaker.Pref.Strength.*;
import static java.lang.Integer.MAX_VALUE;

import java.util.Random;

import csv.Column;

class Student {

    private static final Random RANDOM = new Random();

    static final String BEST = "Ja -- die beste Zeit für mich";
    static final String YES = "Ja -- geht ";
    static final String NO = "Nein -- geht nicht";

    @Column("Ihr Nachname:")
    String nachname;

    @Column("Ihr (bevorzugter) Vorname")
    String vorname;

    @Column("Wie lautet Ihre Legi-Nummer (im Format XX-XXX-XXX)?")
    String legi;

    @Column("Können Sie am Dienstag Nachmittag  (13-15) eine Übungsgruppe besuchen?")
    String tuesday;

    @Column("Können Sie am Mittwoch Morgen   (8-10) eine Übungsgruppe besuchen?")
    String wednesday;

    @Column("Wenn Sie mit Anderen in eine Gruppe eingeteilt werden wollen -- was ist Ihre magische Zahl? Wir versuchen Personen mit der gleichen magischen Zahl in die gleiche Übungsstunde zu verteilen. Eine seltenere magische Zahl ist daher von Vorteil, am besten wählen Sie die Zahl zufällig aus .... Hinweis des Systems:")
    String magicNumber;

    String niceLegi() {
        if (legi.matches("\\d{8}")) {
            return legi.substring(0, 2) + "-" + legi.substring(2, 5)
                    + "-" + legi.substring(5, 8);
        } else {
            return legi;
        }
    }

    Pref pref() {
        if (tuesday.equals(NO) && wednesday.equals(NO)) {
            return Pref.pref(null, NONE);
        } else if (wednesday.equals(NO)) {
            return Pref.pref(TUE, STRONG);
        } else if (tuesday.equals(NO)) {
            return Pref.pref(WED, STRONG);
        } else if (tuesday.equals(BEST) && wednesday.equals(YES)) {
            return Pref.pref(TUE, WEAK);
        } else if (wednesday.equals(BEST) && tuesday.equals(YES)) {
            return Pref.pref(WED, WEAK);
        } else {
            return Pref.pref(null, NONE);
        }
    }

    String name() {
        return vorname + " " + nachname;
    }

    String pseudoMagicNumber() {
        if (hasMagicNumber()) {
            return magicNumber;
        } else {
            return "none (" + RANDOM.nextInt(MAX_VALUE) + ")";
        }
    }

    boolean hasMagicNumber() {
        return !magicNumber.isBlank();
    }
}