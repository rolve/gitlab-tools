package csv;

import static csv.CsvReader.Separator.COMMA;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import csv.Column;
import csv.CsvReader;

public class CsvReaderTest {

    @Test
    public void testRead() throws ReflectiveOperationException {
        var data = asList(
                "Name,E-Mail-Adresse",
                "Hans-Peter Moll,hpmoll@example.com",
                "Frieda Graf,graff@example.com");

        var reader = new CsvReader(COMMA);
        var students = reader.read(data, Student.class);

        assertEquals(2, students.size());
        assertEquals("Hans-Peter Moll", students.get(0).name);
        assertEquals("hpmoll@example.com", students.get(0).email);
        assertEquals("Frieda Graf", students.get(1).name);
        assertEquals("graff@example.com", students.get(1).email);
    }

    public static class Student {
        @Column("Name")
        private String name;
        @Column("E-Mail-Adresse")
        private String email;
    }
}
