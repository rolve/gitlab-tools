package gitlabtools.cmd;

import gitlabtools.GitLabApiIntegrationTest;
import org.gitlab4j.api.models.Project;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateProjectsCmdIT extends GitLabApiIntegrationTest {

    @Test
    public void testDefaults() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa",
                "michael",
                "sarah");
        var args = withTestDefaults(
                "--groupName", GROUP,
                "--courseFile", courseFile.toString());

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(GROUP + "/" + SUBGROUP).stream()
                .map(Project::getName)
                .collect(toSet());
        assertEquals(Set.of("lisa", "michael", "sarah"), projects);
    }

    @Test
    public void testProjectPrefix() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa",
                "michael",
                "sarah");
        var args = withTestDefaults(
                "--groupName", GROUP,
                "--courseFile", courseFile.toString(),
                "--projectNamePrefix", "exercises");

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(GROUP + "/" + SUBGROUP).stream()
                .map(Project::getName)
                .collect(toSet());
        assertEquals(Set.of("exercises_lisa", "exercises_michael", "exercises_sarah"), projects);
    }
}
