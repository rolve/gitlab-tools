package csv;

import static java.lang.reflect.Modifier.isFinal;
import static java.nio.file.Files.newBufferedReader;
import static java.util.Optional.empty;
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

public class CsvReader<E> {

    private final CSVFormat format;
    private final Constructor<E> constr;
    private final Map<String, Field> colsToFields;

    public CsvReader(CSVFormat format, Class<E> clazz) {
        this.format = format;

        try {
            constr = clazz.getDeclaredConstructor();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        constr.setAccessible(true);

        colsToFields = new HashMap<>();
        for (var field : clazz.getDeclaredFields()) {
            columnFor(field).ifPresent(col -> {
                field.setAccessible(true);
                colsToFields.put(col, field);
            });
        }
    }

    private static Optional<String> columnFor(Field f) {
        var col = f.getAnnotation(Column.class);
        if (col == null) {
            return empty();
        } else {
            if (isFinal(f.getModifiers())) {
                throw new AssertionError("annotated field is final");
            } else if (f.getType() != String.class) {
                throw new AssertionError("unsupported field type " + f.getType().getName());
            }
            return Optional.of(col.value());
        }
    }

    public List<E> readAll(Path path) throws IOException {
        try (var stream = read(path)) {
            return stream.collect(toList());
        }
    }

    /**
     * Reads the records from the given {@link Path}. Clients should close the
     * returned stream.
     */
    @SuppressWarnings("resource")
    public Stream<E> read(Path path) throws IOException {
        return read(newBufferedReader(path));
    }

    /**
     * Reads the records from the given {@link Reader}. Clients should close the
     * given reader or the returned stream themselves.
     */
    @SuppressWarnings("resource")
    public Stream<E> read(Reader reader) throws IOException {
        var parser = format.parse(reader);
        return stream(parser.spliterator(), false)
                .map(this::create);
    }

    private E create(CSVRecord record) {
        try {
            var object = constr.newInstance();
            for (var entry : colsToFields.entrySet()) {
                var col = entry.getKey();
                var field = entry.getValue();
                field.set(object, record.get(col));
            }
            return object;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
