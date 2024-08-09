package ch.trick17.gitlabtools.cmd;

import ch.trick17.gitlabtools.GitLabApiIntegrationTest;
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
                "--courseFile", courseFile.toString());

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
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
                "--courseFile", courseFile.toString(),
                "--projectNamePrefix", "exercises");

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());
        var expected = Set.of(
                "exercises_lisa",
                "exercises_michael",
                "exercises_sarah");
        assertEquals(expected, projects);
    }

    @Test
    public void testTeamProjects() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa\t1",
                "michael\t1",
                "sarah\t2",
                "peter\t2",
                "tom\t3",
                "kathrine\t3",
                "paul\t3",
                "mark\t4");
        var args = withTestDefaults(
                "--courseFile", courseFile.toString(),
                "--teamProjects");

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());
        var expected = Set.of(
                "lisa_michael",
                "peter_sarah",
                "kathrine_paul_tom",
                "mark");
        assertEquals(expected, projects);
    }

    @Test
    public void testTeamProjectsPrefix() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa\t1",
                "michael\t1",
                "sarah\t2",
                "peter\t2",
                "tom\t3",
                "kathrine\t3",
                "paul\t3",
                "mark\t4");
        var args = withTestDefaults(
                "--courseFile", courseFile.toString(),
                "--projectNamePrefix", "exercises",
                "--teamProjects");

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());
        var expected = Set.of(
                "exercises_lisa_michael",
                "exercises_peter_sarah",
                "exercises_kathrine_paul_tom",
                "exercises_mark");
        assertEquals(expected, projects);
    }

    @Test
    public void testEmailAddresses() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa@fhnw.ch",
                "michael@example.com",
                "sarah@sarah.example.com");
        var args = withTestDefaults(
                "--courseFile", courseFile.toString());

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());
        assertEquals(Set.of("lisa", "michael", "sarah"), projects);
    }

    @Test
    public void testComment() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa // repeat customer",
                "michael",
                "sarah@sarah.example.com//has no school account yet");
        var args = withTestDefaults(
                "--courseFile", courseFile.toString());

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());
        assertEquals(Set.of("lisa", "michael", "sarah"), projects);
    }

    @Test
    public void testBlankLines() throws Exception {
        var courseFile = writeTempTextFile("course",
                "lisa",
                "",
                "michael",
                "    ",
                "sarah");
        var args = withTestDefaults(
                "--courseFile", courseFile.toString());

        new CreateProjectsCmd(args).execute();

        var projects = api.getGroupApi().getProjects(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());
        assertEquals(Set.of("lisa", "michael", "sarah"), projects);
    }
}
