package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;

public interface Args {
    @Option(defaultValue = "https://gitlab.inf.ethz.ch/")
    String getGitlabUrl();

    @Option(defaultValue = "token.txt")
    String getTokenFile();
}