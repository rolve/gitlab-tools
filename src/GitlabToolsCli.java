import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.lang.Math.random;
import static java.nio.file.Files.readAllLines;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.IssuesApi;

public class GitlabToolsCli {
    
    public static void main(String[] args) throws IOException, GitLabApiException {
        Options options = createCli(Options.class).parseArguments(args);
        
        Path tokenPath = Paths.get(options.getTokenFile());
        String token = readAllLines(tokenPath).get(0);
        
        GitLabApi api = new GitLabApi("https://gitlab.inf.ethz.ch/", token);
        Integer projectId = api.getProjectApi().getProjects(options.getProjectName())
                .stream()
                .filter(p -> p.getName().equals(options.getProjectName()))
                .findFirst().get().getId();
        
        IssuesApi issues = api.getIssuesApi();
        issues.createIssue(projectId, "Test " + random(), "Nothing");
    }
}
