package gitlabtools;

import org.junit.jupiter.api.BeforeAll;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static java.lang.System.currentTimeMillis;
import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.time.temporal.ChronoUnit.SECONDS;

public abstract class GitLabIntegrationTest {

    private static final long MAX_WAIT = 20 * 60 * 1000; // 20 minutes!..

    protected static String url = System.getenv("GITLAB_TOOLS_IT_URL");
    protected static String user = System.getenv("GITLAB_TOOLS_IT_USER");
    protected static String password = System.getenv("GITLAB_TOOLS_IT_PASSWORD");

    @BeforeAll
    public static void waitForGitLabInstance() throws Exception {
        var start = currentTimeMillis();
        var client = HttpClient.newBuilder()
                .followRedirects(ALWAYS)
                .build();
        var request = HttpRequest.newBuilder(new URI(url))
                .timeout(Duration.of(5, SECONDS)).build();
        HttpResponse<Void> response = null;
        do {
            try {
                response = client.send(request, discarding());
            } catch (HttpTimeoutException | ConnectException e) { /* ignore */ }
        } while ((response == null || response.statusCode() != 200)
                && currentTimeMillis() - start < MAX_WAIT);

        if (response == null) {
            throw new RuntimeException("could not connect to GitLab instance");
        } else if (response.statusCode() != 200) {
            throw new RuntimeException("could not connect to GitLab instance (status: " + response.statusCode() + ")");
        }
    }
}
