package ch.trick17.gitlabtools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public class CourseFileReader {

    public static List<String> readSimpleCourseFile(Path path) throws IOException {
        try (var lines = Files.lines(path)) {
            return lines
                    .map(line -> stripComment(line))
                    .map(String::strip)
                    .filter(not(String::isEmpty))
                    .map(raw -> normalizeUsername(raw))
                    .collect(toList());
        }
    }

    public static Collection<? extends Set<String>> readTeamsCourseFile(Path path) throws IOException {
        try (var lines = Files.lines(path)) {
            return lines
                    .map(line -> stripComment(line))
                    .map(String::strip)
                    .filter(not(String::isEmpty))
                    .map(line -> parseTeamsCourseLine(line))
                    .collect(groupingBy(m -> m.team,
                            mapping(m -> m.username, toCollection(TreeSet::new))))
                    .values();
        }
    }

    private static TeamMember parseTeamsCourseLine(String line) {
        var parts = line.split("\\s+");
        if (parts.length != 2) {
            throw new RuntimeException("illegal line in course file: '" + line + "'");
        }
        return new TeamMember(normalizeUsername(parts[0]), parts[1]);
    }

    private static String stripComment(String line) {
        return line.split("//")[0];
    }

    private static String normalizeUsername(String raw) {
        var parts = raw.split("@");
        if (parts.length == 1) {
            return raw;
        }
        if (parts.length == 2) {
            // username is an email address; use only first part
            return parts[0];
        } else {
            throw new RuntimeException("invalid username in course file: " + raw);
        }
    }

    private static class TeamMember {
        String username;
        String team;

        public TeamMember(String username, String team) {
            this.username = username;
            this.team = team;
        }
    }
}
