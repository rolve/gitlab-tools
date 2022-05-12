package gitlabtools;

import gitlabtools.auth.TokenCreator;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static java.lang.String.join;
import static java.lang.System.currentTimeMillis;
import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.time.temporal.ChronoUnit.SECONDS;

public abstract class GitLabIntegrationTest {

    private static final long MAX_WAIT = 300_000; // ms

    protected static String url = System.getenv("GITLAB_TOOLS_IT_URL");
    protected static String user = System.getenv("GITLAB_TOOLS_IT_USER");
    protected static String password = System.getenv("GITLAB_TOOLS_IT_PASSWORD");

    private static String token = null;
    private static Path tokenFile = null;

    protected static String[] withTestDefaults(String... args) {
        return ArrayUtils.addAll(args,
                "--gitlabUrl", url,
                "--tokenFile", tokenFile());
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
        var creator = new TokenCreator(url);
        token = creator.createAccessToken(user, password, "gitlab-tools-it");
        tokenFile = writeTempTextFile("token", token);
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
