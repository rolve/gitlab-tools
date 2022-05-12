package gitlabtools.cmd;

import gitlabtools.GitLabIntegrationTest;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateProjectsCmdIT extends GitLabIntegrationTest {

    public static final String GROUP_NAME = "GitLab Tools Test";
    public static final String GROUP_PATH = "gitlab-tools-test";

    private static GitLabApi api;

    @BeforeAll
    public static void createTestGroup() throws GitLabApiException {
        api = new GitLabApi(url, token());
        try {
            api.getGroupApi().getGroup(GROUP_PATH + "/exercises");
        } catch (GitLabApiException e1) {
            Group group;
            try {
                group = api.getGroupApi().getGroup(GROUP_PATH);
            } catch (GitLabApiException e2) {
                group = api.getGroupApi().addGroup(GROUP_NAME, GROUP_PATH);
            }
            api.getGroupApi().addGroup("exercises", "exercises", null,
                    group.getVisibility(), null, null, group.getId());
        }
    }

    @Test
    public void testDefaults() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa",
                "michael",
                "sarah");
        var args = withTestDefaults(
                "--groupName", GROUP_NAME,
                "--courseFile", courseFile.toString());

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(GROUP_PATH + "/exercises").stream()
                .map(Project::getName)
                .collect(toSet());
        assertEquals(Set.of("lisa", "michael", "sarah"), projects);
    }
}
