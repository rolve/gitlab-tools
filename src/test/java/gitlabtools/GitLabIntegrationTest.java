package gitlabtools;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

@Testcontainers
public abstract class GitLabIntegrationTest {

    protected static String url;
    protected static final String user = "root";
    protected static final String password = "password";

    @Container
    private static final GenericContainer<?> gitlab = new GenericContainer<>("gitlab/gitlab-ce:14.10.5-ce.0")
            .withEnv("GITLAB_HTTPS", "false")
            .withEnv("GITLAB_ROOT_PASSWORD", password)
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/"))
            .withStartupTimeout(Duration.ofMinutes(5));

    @BeforeAll
    public static void setUrl() {
        url = "http://" + gitlab.getHost() + ":" + gitlab.getMappedPort(80);
    }
}
