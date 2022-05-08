package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;

public interface Args {
    @Option(defaultValue = "https://gitlab.fhnw.ch/")
    String getGitlabUrl();

    @Option(defaultValue = "token.txt")
    String getTokenFile();

    @Option
    String getGroupName();

    @Option(defaultValue = "exercises")
    String getSubgroupName();

    @Option(defaultValue = "main")
    String getDefaultBranch();
}
