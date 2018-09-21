import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;
import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;

public class GitlabToolsCli {

    public static void main(String[] args) throws IOException, GitLabApiException {
        var options = createCli(Options.class).parseArguments(args);
        var tools = new GitlabToolsCli(Paths.get(options.getTokenFile()));
        switch (options.getCmd()) {
        case CREATE_ISSUES:
            tools.createStudentIssues(options.getProjectName(), Paths.get(options.getEdozFile()));
            break;
        case CREATE_PROJECTS:
            tools.createStudentProjects(options.getGroupName(), Paths.get(options.getEdozFile()));
            break;
        }
    }

    private final GitLabApi api;
    private final Map<String, User> users = new HashMap<>();

    public GitlabToolsCli(Path accessTokenFile) throws IOException, GitLabApiException {
        var token = readAllLines(accessTokenFile).get(0);
        api = new GitLabApi("https://gitlab.inf.ethz.ch/", token);
        fetchUsers();
    }

    public void createStudentIssues(String projectName, Path edozFile) throws GitLabApiException, IOException {
        var projectId = getProjectId(projectName);

        var students = readEdozFile(edozFile);
        students.forEach(this::findNethzAndUser);

        System.out.printf("Creating issues for %d students...\n", students.size());
        var issues = api.getIssuesApi();
        for (int s = 0; s < students.size(); s++) {
            var student = students.get(s);
            issues.createIssue(projectId, student.toString(), student.legi);
            if (s % 10 == 9) {
                System.out.printf("%d issues created\n", s + 1);
            }
        }
        System.out.println("Done.");
    }

    private int getProjectId(String projectName) throws GitLabApiException {
        return api.getProjectApi().getProjects(projectName).stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst().get().getId();
    }

    private void findNethzAndUser(Student student) {
        student.user = Optional.ofNullable(users.get(student.firstAndLastName));

        var mailParts = student.mail.split("@");
        if (mailParts[0].matches("[a-z]+") && mailParts[1].matches("(student\\.)?ethz\\.ch")) {
            student.nethz = Optional.of(mailParts[0]);
        } else {
            System.err.printf("Warning: unexpected email address %s\n", student.mail);
            student.nethz = student.user.map(User::getUsername);
            if (!student.nethz.isPresent()) {
                System.err.printf("Warning: no nethz name for %s (%s)\n",
                        student.firstAndLastName, student.legi);
            }
        }
    }

    private void createStudentProjects(String groupName, Path edozFile) throws GitLabApiException, IOException {
        var groupId = getGroupId(groupName);

        var students = readEdozFile(edozFile);
        students.forEach(this::findNethzAndUser);

        System.out.printf("Creating projects for %d students...\n", students.size());
        var projects = api.getProjectApi();
        for (int s = 0; s < students.size(); s++) {
            var student = students.get(s);
            if (student.nethz.isPresent()) {
                Project project = projects.createProject(groupId, student.nethz.get());
                api.getProjectApi().addMember(project.getId(), student.user.get().getId(), DEVELOPER);
            } else {
                System.err.printf("Warning: no project created for %s\n", student.firstAndLastName);
            }
            if (s % 10 == 9) {
                System.out.printf("%d projects created\n", s + 1);
            }
        }
        System.out.println("Done.");
    }

    private int getGroupId(String groupName) throws GitLabApiException {
        return api.getGroupApi().getGroups(groupName).stream()
                .filter(g -> g.getName().equals(groupName))
                .findFirst().get().getId();
    }

    private static List<Student> readEdozFile(Path edozFile) throws IOException {
        return readAllLines(edozFile).stream()
                .skip(1)
                .map(line -> new Student(line.split("\t")))
                .collect(toList());
    }

    private void fetchUsers() throws GitLabApiException {
        System.out.println("Fetching users...");
        api.getUserApi().getUsers(MAX_VALUE).forEachRemaining(page -> {
            page.forEach(user -> {
                if (users.put(user.getName(), user) != null) {
                    System.err.printf("Warning: multiple users with name %s!\n", user.getName());
                }
            });
        });
        System.out.printf("%d users fetched\n", users.size());
    }

    static class Student {
        final String legi;
        final String firstAndLastName;
        final String mail;
        Optional<String> nethz = empty();
        Optional<User> user = empty();

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
