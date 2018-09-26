import static org.apache.commons.lang3.ArrayUtils.subarray;

import java.util.HashMap;
import java.util.Map;

public class GitlabToolsCli {
    
    private static Map<String, Class<? extends Cmd<?>>> commands = new HashMap<>() {{
        put("create-issues", CreateIssuesCmd.class);
        put("create-projects", CreateProjectsCmd.class);
        put("create-room-projects", CreateRoomProjectsCmd.class);
        put("assign-members", AssignMembersCmd.class);
        put("publish", PublishCmd.class);
    }};

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("no command specified");
            return;
        }
        
        var cmdClass = commands.get(args[0]);
        if (cmdClass == null) {
            System.err.println("unknown command " + args[0]);
        }
        var cmd = cmdClass.getConstructor(String[].class)
                .newInstance(new Object[] { subarray(args, 1, args.length) });
        
        cmd.call();
    }
}
