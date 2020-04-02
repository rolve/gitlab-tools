package csv;

import static java.lang.reflect.Modifier.isFinal;
import static java.nio.file.Files.newBufferedReader;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class CsvReader {

    private CSVFormat format;

    public CsvReader(CSVFormat format) {
        this.format = format;
    }

    public <E> List<E> readAll(Path path, Class<E> clazz) throws IOException {
        try (var stream = read(path, clazz)) {
            return stream.collect(toList());
        }
    }

    /**
     * Reads the records from the given {@link Path}. Clients should close the
     * returned stream.
     */
    @SuppressWarnings("resource")
    public <E> Stream<E> read(Path path, Class<E> clazz) throws IOException {
        return read(newBufferedReader(path), clazz);
    }

    /**
     * Reads the records from the given {@link Reader}. Clients should close the
     * given reader or the returned stream themselves.
     */
    @SuppressWarnings("resource")
    public <E> Stream<E> read(Reader reader, Class<E> clazz) throws IOException {
        var parser = format.parse(reader);

        Constructor<E> constr;
        try {
            constr = clazz.getDeclaredConstructor();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        constr.setAccessible(true);

        var colsToFields = new HashMap<String, Set<Field>>();
        for (var f : clazz.getDeclaredFields()) {
            var name = colNameFor(f);
            if (name != null) {
                colsToFields.computeIfAbsent(name, k -> new HashSet<>()).add(f);
                f.setAccessible(true);
            }
        }

        return stream(parser.spliterator(), false)
                .map(record -> create(constr, colsToFields, record));
    }

    private <E> E create(Constructor<E> constr,
            Map<String, Set<Field>> colsToFields, CSVRecord record) {
        try {
            var object = constr.newInstance();
            for (var entry : colsToFields.entrySet()) {
                var name = entry.getKey();
                for (var field : entry.getValue()) {
                    field.set(object, record.get(name));
                }
            }
            return object;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
