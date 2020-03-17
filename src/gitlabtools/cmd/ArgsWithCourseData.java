package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;

public interface ArgsWithCourseData extends Args {
    @Option(defaultValue = "edoz.txt") // tab-separated
    String getCourseFile();

    @Option(defaultValue = "specialnethz.txt") // tab-separated file with legi & username
    String getSpecialUsernameFile();

    /*
     * The following two patterns are used to decide whether an email address
     * can be used to determine the GitLab username.
     */

    @Option(defaultValue = "[a-z]+")
    String getLocalPartPattern();

    @Option(defaultValue = "(student\\.)?ethz\\.ch")
    String getDomainPattern();
}
