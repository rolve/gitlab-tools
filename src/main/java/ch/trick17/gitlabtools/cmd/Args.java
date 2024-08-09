package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;

public interface Args {
    @Option(defaultValue = "https://gitlab.fhnw.ch/")
    String getGitLabUrl();

    @Option(defaultValue = "token.txt")
    String getTokenFile();

    @Option
    String getGroup();

    @Option(helpRequest = true)
    boolean getHelp();
}
