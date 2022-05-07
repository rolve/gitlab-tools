package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;

public interface ArgsWithCourseData extends Args {
    @Option(defaultValue = "course.txt") // tab-separated
    String getCourseFile();

    @Option(defaultValue = "specialusers.txt") // tab-separated file with email & username
    String getSpecialUsersFile();

    /*
     * The following two patterns are used to decide whether an email address
     * can be used to determine the GitLab username.
     */

    @Option(defaultValue = ".*")
    String getLocalPartPattern();

    @Option(defaultValue = ".*")
    String getDomainPattern();
}
