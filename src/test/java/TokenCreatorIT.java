import gitlabtools.auth.TokenCreator;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TokenCreatorIT {

    String url = System.getenv("GITLAB_TOOLS_IT_URL");
    String user = System.getenv("GITLAB_TOOLS_IT_USER");
    String password = System.getenv("GITLAB_TOOLS_IT_PASSWORD");

    @Test
    public void testCreateAccessToken() throws GitLabApiException {
        var creator = new TokenCreator(url);
        var token = creator.createAccessToken(user, password, "gitlab-tools-it");
        assertNotNull(token);

        // test that access actually works
        var api = new GitLabApi(url, token);
        var user = api.getUserApi().getUser(this.user);
        assertNotNull(user);
    }
}
