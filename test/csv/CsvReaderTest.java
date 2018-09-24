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
        assertEquals("Hans-Peter Moll", students.get(0).nameAgain);
        assertEquals("hpmoll@example.com", students.get(0).email);
        assertEquals(null, students.get(0).nethz);
        assertEquals(null, students.get(0).nethzAgain);
        assertEquals("Frieda Graf", students.get(1).name);
        assertEquals("Frieda Graf", students.get(1).nameAgain);
        assertEquals("graff@example.com", students.get(1).email);
        assertEquals(null, students.get(1).nethz);
        assertEquals(null, students.get(1).nethzAgain);
    }

    public static class Student {
        @Column("Name")
        private String name;

        @ColumnIndex(0)
        private String nameAgain;

        @Column("E-Mail-Adresse")
        private String email;

        @Column("NETHZ")
        private String nethz;

        @ColumnIndex(3)
        private String nethzAgain;
    }
}
