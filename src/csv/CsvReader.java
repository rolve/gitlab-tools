package csv;

import static java.lang.reflect.Modifier.isFinal;
import static java.nio.file.Files.readAllLines;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

import java.io.IOException;
import java.lang.reflect.Constructor;
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

        var constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);

        var colsToFields = new HashMap<Integer, Set<Field>>();
        for (var f : clazz.getDeclaredFields()) {
            var index = colIndexFor(f, header);
            if (index != null) {
                colsToFields.computeIfAbsent(index, k -> new HashSet<>()).add(f);
                f.setAccessible(true);
            }
        }

        var result = new ArrayList<E>();
        for(var line : lines.subList(1, lines.size())) {
            var cells = line.split(separator.regex);
            if (cells.length != header.size()) {
                throw new IllegalCsvFormatException("unexpected number of cells");
            }
            var object = constructor.newInstance();
            for(int c = 0; c < cells.length; c++) {
                for (var f : colsToFields.getOrDefault(c, emptySet())) {
                    f.set(object, cells[c]);
                }
            }
            result.add(object);
        }
        return result;
    }

    private Integer colIndexFor(Field f, List<String> header) {
        var col = f.getAnnotation(Column.class);
        var colIndex = f.getAnnotation(ColumnIndex.class);

        if (col != null && colIndex != null) {
            throw new AssertionError("Column and ColumnIndex not allowed on same field");
        } else if (colIndex != null) {
            checkField(f);
            if (colIndex.value() < 0) {
                throw new AssertionError("negative ColumnIndex annotation");
            } else if(colIndex.value() >= header.size()) {
                System.err.printf("Warning: missing column %d\n", colIndex.value());
            }
            return colIndex.value();
        } else if (col != null) {
            checkField(f);
            int index = header.indexOf(col.value());
            if (index == -1) {
                System.err.printf("Warning: missing column \"%s\"\n", col.value());
            }
            return index;
        } else {
            return null;
        }
    }

    private void checkField(Field f) {
        if (isFinal(f.getModifiers())) {
            throw new AssertionError("annotated field is final");
        } else if (f.getType() != String.class) {
            throw new AssertionError("unsupported field type " + f.getType().getName());
        }
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
