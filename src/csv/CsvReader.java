package csv;

import static java.lang.reflect.Modifier.isFinal;
import static java.nio.file.Files.newBufferedReader;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class CsvReader {

    private CSVFormat format;

    public CsvReader(CSVFormat format) {
        this.format = format;
    }

    public <E> List<E> read(Path path, Class<E> clazz) throws Exception {
        return read(newBufferedReader(path), clazz);
    }

    public <E> List<E> read(Reader reader, Class<E> clazz) throws Exception {
        var parser = format.parse(reader);

        var constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);

        var colsToFields = new HashMap<String, Set<Field>>();
        for (var f : clazz.getDeclaredFields()) {
            var name = colNameFor(f);
            if (name != null) {
                colsToFields.computeIfAbsent(name, k -> new HashSet<>()).add(f);
                f.setAccessible(true);
            }
        }

        var result = new ArrayList<E>();
        for (CSVRecord record : parser) {
            var object = constructor.newInstance();
            for (var entry : colsToFields.entrySet()) {
                var name = entry.getKey();
                for (var field : entry.getValue()) {
                    field.set(object, record.get(name));
                }
            }
            result.add(object);
        }
        return result;
    }

    private String colNameFor(Field f) {
        var col = f.getAnnotation(Column.class);
        if (col == null) {
            return null;
        } else {
            if (isFinal(f.getModifiers())) {
                throw new AssertionError("annotated field is final");
            } else if (f.getType() != String.class) {
                throw new AssertionError("unsupported field type " + f.getType().getName());
            }
            return col.value();
        }
    }
}
