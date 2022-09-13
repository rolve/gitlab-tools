package gitlabtools.auth;

import gitlabtools.GitLabIntegrationTest;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TokenCreatorIT extends GitLabIntegrationTest {

    @Test
    public void testCreateAccessToken() throws GitLabApiException, AuthenticationException, TokenCreationException {
        var creator = new TokenCreator(url);
        var token = creator.createAccessToken(user, password, "gitlab-tools-it");
        assertNotNull(token);

        // test that access actually works
        var api = new GitLabApi(url, token);
        assertNotNull(api.getUserApi().getUser(user));
    }
}
