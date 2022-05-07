package csv;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.csv.CSVFormat.EXCEL;
import static org.junit.Assert.assertEquals;

import java.io.StringReader;

import org.junit.Test;

public class CsvReaderTest {

    @Test
    public void testRead() throws Exception {
        var data = 
                "Name,E-Mail-Adresse\r\n" +
                "Hans-Peter Moll,hpmoll@example.com\r\n" +
                "Frieda Graf,graff@example.com\r\n";

        var students = new CsvReader<>(EXCEL.withHeader(), Student.class)
                .read(new StringReader(data))
                .collect(toList());

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
