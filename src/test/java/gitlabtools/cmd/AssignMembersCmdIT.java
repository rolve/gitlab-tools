package gitlabtools.cmd;

import gitlabtools.GitLabApiIntegrationTest;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AssignMembersCmdIT extends GitLabApiIntegrationTest {

    List<String> usernames = List.of("lisa", "markus", "sarah", "tom",
            "sandra", "michael", "boris", "mia");
    List<User> users = new ArrayList<>();

    @BeforeEach
    public void createUsers() throws GitLabApiException {
        for (var username : usernames) {
            var user = api.getUserApi().getUser(username);
            if (user == null) {
                user = new User()
                        .withUsername(username)
                        .withName(username)
                        .withEmail(username + "@example.com");
                user = api.getUserApi().createUser(user, "password", false);
            }
            users.add(user);
        }
    }

    @Test
    public void testDefaults() throws Exception {
        // create projects
        var projects = new ArrayList<Project>();
        for (var username : usernames) {
            projects.add(api.getProjectApi().createProject(subgroup.getId(), username));
        }

        var args = withTestDefaults();
        new AssignMembersCmd(args).execute();

        for (int i = 0; i < projects.size(); i++) {
            var userIds = api.getProjectApi().getAllMembersStream(projects.get(i))
                    .map(Member::getId)
                    .collect(toSet());
            var expected = Set.of(
                    api.getUserApi().getCurrentUser().getId(), // member of the group
                    users.get(i).getId());
            assertEquals(expected, userIds);
        }
    }

    @Test
    public void testProjectPrefix() throws Exception {
        // create projects
        var projects = new ArrayList<Project>();
        for (var username : usernames) {
            projects.add(api.getProjectApi().createProject(subgroup.getId(), "exercises_" + username));
        }

        var args = withTestDefaults("--withProjectNamePrefix");
        new AssignMembersCmd(args).execute();

        for (int i = 0; i < projects.size(); i++) {
            var userIds = api.getProjectApi().getAllMembersStream(projects.get(i))
                    .map(Member::getId)
                    .collect(toSet());
            var expected = Set.of(
                    api.getUserApi().getCurrentUser().getId(), // member of the group
                    users.get(i).getId());
            assertEquals(expected, userIds);
        }
    }

    @Test
    public void testTeamProjects() throws Exception {
        // create team projects
        int[][] teams = {
                {0, 1, 2},
                {3, 4},
                {5, 6},
                {7}};

        var projects = new ArrayList<Project>();
        for (var team : teams) {
            var name = IntStream.of(team).mapToObj(usernames::get).collect(joining("_"));
            projects.add(api.getProjectApi().createProject(subgroup.getId(), name));
        }

        var args = withTestDefaults("--teamProjects");
        new AssignMembersCmd(args).execute();

        for (int i = 0; i < projects.size(); i++) {
            var userIds = api.getProjectApi().getAllMembersStream(projects.get(i))
                    .map(Member::getId)
                    .collect(toSet());

            var expected = new HashSet<>();
            expected.add(api.getUserApi().getCurrentUser().getId()); // member of the group
            for (int u : teams[i]) {
                expected.add(users.get(u).getId());
            }
            assertEquals(expected, userIds);
        }
    }

    @Test
    public void testTeamProjectsPrefix() throws Exception {
        // create team projects
        int[][] teams = {
                {0, 1, 2},
                {3, 4},
                {5, 6},
                {7}};

        var projects = new ArrayList<Project>();
        for (var team : teams) {
            var joined = IntStream.of(team).mapToObj(usernames::get).collect(joining("_"));
            var name = "project_" + joined;
            projects.add(api.getProjectApi().createProject(subgroup.getId(), name));
        }

        var args = withTestDefaults(
                "--teamProjects",
                "--withProjectNamePrefix");
        new AssignMembersCmd(args).execute();

        for (int i = 0; i < projects.size(); i++) {
            var userIds = api.getProjectApi().getAllMembersStream(projects.get(i))
                    .map(Member::getId)
                    .collect(toSet());

            var expected = new HashSet<>();
            expected.add(api.getUserApi().getCurrentUser().getId()); // member of the group
            for (int u : teams[i]) {
                expected.add(users.get(u).getId());
            }
            assertEquals(expected, userIds);
        }
    }
}
