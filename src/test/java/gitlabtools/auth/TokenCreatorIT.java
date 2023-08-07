package gitlabtools.auth;

import gitlabtools.GitLabIntegrationTest;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TokenCreatorIT extends GitLabIntegrationTest {

    @Test
    public void testCreateAccessToken() throws GitLabApiException, AuthenticationException, TokenCreationException {
        var creator = new TokenCreator(URL);
        var token = creator.createAccessToken(USER, PASSWORD, "gitlab-tools-it");
        assertNotNull(token);

        // test that access actually works
        var api = new GitLabApi(URL, token);
        assertNotNull(api.getUserApi().getUser(USER));
    }
}
