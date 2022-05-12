package gitlabtools;

import gitlabtools.auth.TokenCreator;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.String.join;

public abstract class GitLabIntegrationTest {
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
}
