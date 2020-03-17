import com.lexicalscope.jewel.cli.Option;

public interface ArgsWithProjectAccess extends Args {
    @Option
    String getGroupName();

    @Option(defaultValue = "students")
    String getSubgroupName();
}
