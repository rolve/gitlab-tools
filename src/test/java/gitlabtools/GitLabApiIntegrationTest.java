package gitlabtools;

import gitlabtools.auth.AuthenticationException;
import gitlabtools.auth.TokenCreationException;
import gitlabtools.auth.TokenCreator;
import org.apache.commons.lang3.ArrayUtils;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.String.join;

public class GitLabApiIntegrationTest extends GitLabIntegrationTest {

    protected static final String GROUP = "gitlab-tools-test";
    protected static final String SUBGROUP = "exercises";

    private static String token = null;
    private static Path tokenFile = null;

    protected static String[] withTestDefaults(String... args) {
        return ArrayUtils.addAll(args,
                "--gitlabUrl", url,
                "--tokenFile", tokenFile(),
                "--groupName", GROUP);
    }

    protected static String token() {
        if (token == null) {
            createToken();
        }
        return token;
    }

    protected static String tokenFile() {
        if (token == null) {
            createToken();
        }
        return tokenFile.toString();
    }

    private static void createToken() {
        try {
            var creator = new TokenCreator(url);
            token = creator.createAccessToken(user, password, "gitlab-tools-it");
            tokenFile = writeTempTextFile("token", token);
        } catch (TokenCreationException | AuthenticationException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Path writeTempTextFile(String prefix, String... lines) {
        try {
            var file = Files.createTempFile(prefix, ".txt");
            Files.writeString(file, join("\n", lines));
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected GitLabApi api = new GitLabApi(url, token());
    protected Group subgroup;

    @BeforeEach
    public void createGroups() throws GitLabApiException {
        try {
            subgroup = api.getGroupApi().getGroup(GROUP + "/" + SUBGROUP);
        } catch (GitLabApiException e1) {
            Group group;
            try {
                group = api.getGroupApi().getGroup(GROUP);
            } catch (GitLabApiException e2) {
                group = api.getGroupApi().addGroup(GROUP, GROUP);
            }
            subgroup = api.getGroupApi().addGroup(SUBGROUP, SUBGROUP, null,
                    group.getVisibility(), null, null, group.getId());
        }
    }

    @AfterEach
    public void deleteProjects() throws GitLabApiException, InterruptedException {
        for (var project : api.getGroupApi().getProjects(subgroup)) {
            api.getProjectApi().deleteProject(project);
        }
        // apparently, deletion is somehow delayed, causing subsequent
        // tests to fail sporadically if they create projects with the
        // same name. hence, wait a couple of seconds here...
        Thread.sleep(10_000);
    }
}
