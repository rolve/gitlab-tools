import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.IssuesApi;

public class GitlabToolsCli {

    public static void main(String[] args) throws IOException, GitLabApiException {
        Options options = createCli(Options.class).parseArguments(args);
        GitlabToolsCli tools = new GitlabToolsCli(Paths.get(options.getTokenFile()));
        tools.createStudentIssues(options.getProjectName(), Paths.get(options.getEdozFile()));
    }

    private final GitLabApi api;
    private final Map<String, String> usernames = new HashMap<>();

    public GitlabToolsCli(Path accessTokenFile) throws IOException, GitLabApiException {
        String token = readAllLines(accessTokenFile).get(0);
        api = new GitLabApi("https://gitlab.inf.ethz.ch/", token);
    }

    public void createStudentIssues(String projectName, Path edozFile) throws GitLabApiException, IOException {
        int projectId = getProjectId(api, projectName);

        System.out.println("Fetching users...");
        api.getUserApi().getUsers(MAX_VALUE).forEachRemaining(page -> {
            page.forEach(user -> usernames.put(user.getName(), user.getUsername()));
        });
        System.out.printf("%d users fetched\n", usernames.size());

        List<Student> students = readEdozFile(edozFile);
        students.forEach(this::inferNethzName);

        System.out.printf("Creating issues for %d students...\n", students.size());
        IssuesApi issues = api.getIssuesApi();
        for (int s = 0; s < students.size(); s++) {
            Student student = students.get(s);
            issues.createIssue(projectId, student.toString(), student.legi);
            if (s % 10 == 9) {
                System.out.printf("%d issues created\n", s + 1);
            }
        }
        System.out.println("Done.");
    }

    private static int getProjectId(GitLabApi api, String projectName) throws GitLabApiException {
        return api.getProjectApi().getProjects(projectName).stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst().get().getId();
    }

    private static List<Student> readEdozFile(Path edozFile) throws IOException {
        return readAllLines(edozFile).stream()
                .skip(1)
                .map(line -> new Student(line.split("\t")))
                .collect(toList());
    }

    private void inferNethzName(Student student) {
        String[] mailParts = student.mail.split("@");
        if (mailParts[0].matches("[a-z]+") && mailParts[1].matches("(student\\.)?ethz\\.ch")) {
            student.nethz = Optional.of(mailParts[0]);
        } else {
            System.err.printf("Warning: unexpected email address %s\n", student.mail);
            student.nethz = Optional.ofNullable(usernames.get(student.firstAndLastName));
            if (!student.nethz.isPresent()) {
                System.err.printf("Warning: no nethz name for %s (%s)\n",
                        student.firstAndLastName, student.legi);
            }
        }
    }

    static class Student {
        final String legi;
        final String firstAndLastName;
        final String mail;
        Optional<String> nethz = empty();

        Student(String[] cells) {
            legi = cells[3];
            firstAndLastName = cells[2] + " " + cells[0];
            mail = cells[22];
        }
        
        public String toString() {
            return format("%s (%s)", firstAndLastName, nethz.orElse("???"));
        }
    }
}
