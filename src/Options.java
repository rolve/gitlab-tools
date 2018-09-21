import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

public interface Options {
    
    @Option(defaultToNull = true)
    String getProjectName();
    
    @Option(defaultToNull = true)
    String getGroupName();

    @Option(defaultValue = {"token.txt"})
    String getTokenFile();
    
    @Option(defaultValue = {"edoz.txt"})
    String getEdozFile();
    
    @Unparsed
    Cmd getCmd();
}
