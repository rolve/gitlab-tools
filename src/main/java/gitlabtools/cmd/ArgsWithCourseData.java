package gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;

public interface ArgsWithCourseData extends Args {
    @Option(defaultValue = "course.txt") // tab-separated
    String getCourseFile();
}
