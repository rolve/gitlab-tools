import com.lexicalscope.jewel.cli.Option;

public interface Options {
    
    @Option
    String getProjectName();

    @Option(defaultValue = {"token.txt"})
    String getTokenFile();
}
