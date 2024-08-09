package ch.trick17.gitlabtools;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public abstract class GitLabIntegrationTest {

    protected static final String URL;
    protected static final String USER = "root";
    protected static final String PASSWORD = "password";

    static {
        var gitlab = new GenericContainer<>("gitlab/gitlab-ce:14.10.5-ce.0")
                .withEnv("GITLAB_HTTPS", "false")
                .withEnv("GITLAB_ROOT_PASSWORD", PASSWORD)
                .withExposedPorts(80)
                .waitingFor(Wait.forHttp("/"))
                .withStartupTimeout(Duration.ofMinutes(5));
        gitlab.start();
        URL = "http://" + gitlab.getHost() + ":" + gitlab.getMappedPort(80);
    }
}
