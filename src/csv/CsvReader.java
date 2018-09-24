package csv;

import static java.nio.file.Files.readAllLines;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CsvReader {

    private final Separator separator;

    public CsvReader(Separator separator) {
        this.separator = separator;
    }

    public <E> List<E> read(Path path, Class<E> clazz) throws IOException, ReflectiveOperationException {
        return read(readAllLines(path), clazz);
    }

    public <E> List<E> read(List<String> lines, Class<E> clazz) throws ReflectiveOperationException {
        var header = asList(lines.get(0).split(separator.regex));
        if (new HashSet<>(header).size() < header.size()) {
            throw new IllegalCsvFormatException("header contains duplicate entries");
        }

        var columnsToFields = new HashMap<Integer, Set<Field>>();
        for (var f : clazz.getDeclaredFields()) {
            var column = f.getAnnotation(Column.class);
            var index = f.getAnnotation(ColumnIndex.class);

            if (column != null && index != null) {
                throw new AssertionError("Column and ColumnIndex not allowed on same field");
            } else if (index != null) {
                if (index.value() < 0 || index.value() >= header.size()) {
                    throw new IllegalCsvFormatException("unexpected number of columns in header");
                }
                columnsToFields.computeIfAbsent(index.value(),
                        k -> new HashSet<>()).add(f);
            } else if (column != null) {
                columnsToFields.computeIfAbsent(header.indexOf(column.value()),
                        k -> new HashSet<>()).add(f);
            }
        }

        columnsToFields.values().stream().flatMap(Set::stream)
                .forEach(f -> f.setAccessible(true));

        var result = new ArrayList<E>();
        for(var line : lines.subList(1, lines.size())) {
            var cells = line.split(separator.regex);
            if (cells.length != header.size()) {
                throw new IllegalCsvFormatException("unexpected number of cells");
            }
            var object = clazz.getConstructor().newInstance();
            for(int c = 0; c < cells.length; c++) {
                for (var f : columnsToFields.getOrDefault(c, emptySet())) {
                    f.set(object, cells[c]);
                }
            }
            result.add(object);
        }
        return result;
    }

    public enum Separator {
        COMMA(","), SEMICOLON(";"), TAB("\t");
        
        private String regex;

        private Separator(String regex) {
            this.regex = regex;
        }
    }

    public static class IllegalCsvFormatException extends RuntimeException {
        public IllegalCsvFormatException(String message) {
            super(message);
        }
    }
}
