import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.Files.*;
import static java.util.Collections.reverseOrder;
import static java.util.Collections.shuffle;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.api.Git.open;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

import spoon.Launcher;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.support.compiler.VirtualFile;

public class ExportSourcesCmd extends CmdWithEdoz<ExportSourcesCmd.Args> {

    private CredentialsProvider credentials;
    private int cloned;

    public ExportSourcesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");

        credentials = new UsernamePasswordCredentialsProvider("", token);

        var destDir = Paths.get(args.getDestinationDir());
        createDirectories(destDir);

        var projects = getProjectsIn(studGroup);

        var numbers = range(0, projects.size())
                .mapToObj(Integer::toString).collect(toList());
        shuffle(numbers);
        var newNames = numbers.iterator();

        cloned = 0;
        for (var project : projects) {
            var repoDir = destDir.resolve(project.getName());

            checkout(project.getWebUrl(), repoDir);
            deleteRecursive(repoDir.resolve(".git"));
            removeNonSubmissions(repoDir);
            removeNonSources(repoDir);
            removeEmptyDirs(repoDir);

            var student = students.stream()
                    .filter(s -> s.nethz.get().equals(project.getName()))
                    .findFirst();
            if (student.isPresent()) {
                var stud = student.get();
                var tokens = List.of(stud.nethz.get(), stud.firstName, stud.lastName);
                anonymizeSources(repoDir, tokens);
            } else {
                System.err.println("No EDoz entry for " + project.getName());
            }

            move(repoDir, destDir.resolve(newNames.next()));
        }

        System.out.printf("Done. %d submissions exported (%d newly cloned)\n",
                projects.size(), cloned);
    }

    private void checkout(String projectUrl, Path repoDir) throws GitAPIException, IOException {
        int attempts = 2;
        while (attempts-- > 0) {
            try {
                var clone = true;
                if (exists(repoDir)) {
                    var success = tryPull(repoDir);
                    clone = !success;
                }
                if (clone) {
                    cloneRepository()
                            .setURI(projectUrl)
                            .setDirectory(repoDir.toFile())
                            .setCredentialsProvider(credentials)
                            .call()
                            .close();
                    cloned++;
                }
                break; // done
            } catch (TransportException e) {
                if (attempts == 0) {
                    throw e;
                } else {
                    e.printStackTrace(System.err);
                    System.err.println("Transport exception! Attempts left: " + attempts);
                }
            }
        }
    }

    private boolean tryPull(Path repoDir) throws IOException {
        try (Git git = open(repoDir.toFile())) {
            git.pull()
                    .setCredentialsProvider(credentials)
                    .call();
            return true;
        } catch (Exception e) {
            // something went wrong before, delete everything and clone
            e.printStackTrace();
            System.err.println("Deleting " + repoDir + " and trying a fresh clone...");
            deleteRecursive(repoDir);
            return false;
        }
    }

    private void removeNonSubmissions(Path dir) throws IOException {
        try (var paths = list(dir)) {
            var nonSubs = paths.filter(p ->
                    !isDirectory(p) ||
                    !p.getFileName().toString().matches("u\\d+"));
            for (var p : iterable(nonSubs)) {
                deleteRecursive(p);
            }
        }
    }

    private void deleteRecursive(Path path) throws IOException {
        try (var paths = Files.walk(path).sorted(reverseOrder())) {
            for (var p : iterable(paths)) {
                p.toFile().setWritable(true);
                delete(p);
            }
        }
    }

    private void removeNonSources(Path dir) throws IOException {
        try (var paths = walk(dir)) {
            var filtered = paths.filter(p ->
                    isRegularFile(p) &&
                    !p.toString().toLowerCase().endsWith(".java"));
            for (var p : iterable(filtered)) {
                delete(p);
            }
        }
    }

    private void removeEmptyDirs(Path dir) throws IOException {
        try (var paths = walk(dir)) {
            var dirs = paths.sorted(reverseOrder())
                    .filter(Files::isDirectory);
            for (var d : iterable(dirs)) {
                if (!list(d).findAny().isPresent()) {
                    delete(d);
                }
            }
        }
    }

    private void anonymizeSources(Path repoDir, List<String> tokens) throws IOException {
        try (var files = walk(repoDir).filter(Files::isRegularFile)) {
            for (var f : iterable(files)) {
                if (!linesWithName(f, tokens).isEmpty()) {
                    try {
                        var code = readAllLinesRobust(f).stream().collect(joining("\n"));
                        var anonymized = anonymize(code, tokens);
                        write(f, List.of(anonymized), UTF_8);
                    } catch (RuntimeException e) {
                        System.err.println("issues with " + repoDir.getFileName());
                        e.printStackTrace();
                    }
                }
                // if name is still somewhere, report
                var lines = linesWithName(f, tokens);
                if (!lines.isEmpty()) {
                    System.out.println(f + " contains one of " +
                            tokens.stream().collect(joining(", ")));
                    lines.stream().map(l -> "    " + l.trim()).forEach(System.out::println);
                }
            }
        }
    }

    private List<String> linesWithName(Path f, List<String> tokens) throws IOException {
        var patterns = toPatterns(tokens);
        return readAllLinesRobust(f).stream()
                .filter(l -> patterns.stream().anyMatch(p -> p.matcher(l).find()))
                .collect(toList());
    }

    private String anonymize(String code, List<String> tokens) {
        var spoon = new Launcher();
        spoon.addInputResource(new VirtualFile(code));
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(true);
        var types = spoon.buildModel().getAllTypes();

        var builder = new StringBuilder();
        for (var type : types) {
            type.accept(new CtScanner() {
                public void visitCtComment(CtComment comment) {
                    super.visitCtComment(comment);
                    handleComment(comment);
                }
                public void visitCtJavaDoc(CtJavaDoc javaDoc) {
                    super.visitCtJavaDoc(javaDoc);
                    handleComment(javaDoc);
                }
                private void handleComment(CtComment comment) {
                    var anonymized = anonymizeString(comment.getContent(), tokens);
                    // work around Spoon bug
                    if (anonymized.startsWith("/")) {
                        anonymized = "\n" + anonymized;
                    }
                    comment.setContent(anonymized);
                }
                public <T> void visitCtLiteral(CtLiteral<T> literal) {
                    super.visitCtLiteral(literal);
                    if (literal.getType().getQualifiedName().equals("java.lang.String")) {
                        @SuppressWarnings("unchecked")
                        T anonymized = (T) anonymizeString((String) literal.getValue(), tokens);
                        literal.setValue(anonymized);
                    }
                }
            });
            var printer = new DefaultJavaPrettyPrinter(spoon.getEnvironment());
            type.accept(printer);
            builder.append(printer.getResult());
            builder.append("\n\n");
        }
        return builder.toString();
    }

    private String anonymizeString(String content, List<String> tokens) {
        var patterns = toPatterns(tokens);
        var cleaned = content;
        for (var pattern : patterns) {
            cleaned = pattern.matcher(cleaned).replaceAll("???");
        }
        return cleaned;
    }

    private List<Pattern> toPatterns(List<String> tokens) {
        return tokens.stream().map(t -> {
            if (t.length() <= 3) {
                // short tokens are only considered when they appear as a whole,
                // i.e., not as part of a longer word
                return compile("(?i)\\b" + quote(t) + "\\b");
            } else {
                return compile("(?i)" + quote(t));
            }
        }).collect(toList());
    }

    private List<String> readAllLinesRobust(Path f) throws IOException {
        for (var charset : List.of(UTF_8, ISO_8859_1, US_ASCII)) {
            try {
                return readAllLines(f, charset);
            } catch (CharacterCodingException e) {
                // try next
            }
        }
        throw new AssertionError("no matching encoding found for " + f);
    }

    interface Args extends CmdWithEdoz.Args {
        @Option
        String getGroupName();

        @Option
        String getDestinationDir();
    }
}
