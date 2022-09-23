package gitlabtools;

import gitlabtools.auth.AuthenticationException;
import gitlabtools.auth.TokenCreationException;
import gitlabtools.auth.TokenCreator;
import org.apache.commons.lang3.ArrayUtils;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static java.lang.String.join;

public class GitLabApiIntegrationTest extends GitLabIntegrationTest {

    private static final String GROUP = "gitlab-tools-test";

    private static String token = null;
    private static Path tokenFile = null;

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
        var subgroupName = "group" + new Random().nextLong();
        Group group;
        try {
            group = api.getGroupApi().getGroup(GROUP);
        } catch (GitLabApiException e2) {
            group = api.getGroupApi().addGroup(GROUP, GROUP);
        }
        subgroup = api.getGroupApi().addGroup(subgroupName, subgroupName, null,
                group.getVisibility(), null, null, group.getId());
    }

    protected String[] withTestDefaults(String... args) {
        return ArrayUtils.addAll(args,
                "--gitlabUrl", url,
                "--tokenFile", tokenFile(),
                "--groupName", GROUP,
                "--subgroupName", subgroup.getName());
    }
}
