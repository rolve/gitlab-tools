import com.lexicalscope.jewel.cli.Option;

public interface ArgsWithCourseData extends Args {
    @Option(defaultValue = "edoz.txt") // tab-separated
    String getCourseFile();

    @Option(defaultValue = "specialnethz.txt") // tab-separated file with legi & username
    String getSpecialUsernameFile();
}
