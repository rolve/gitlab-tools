import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.ArrayUtils.subarray;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class GitlabToolsCli {
    
    private static Map<String, Class<? extends Cmd<?>>> commands = new HashMap<>() {{
        put("create-issues", CreateIssuesCmd.class);
        put("create-projects", CreateProjectsCmd.class);
        put("create-room-projects", CreateRoomProjectsCmd.class);
        put("assign-members", AssignMembersCmd.class);
        put("publish", PublishCmd.class);
        put("publish-fast", PublishFastCmd.class);
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

        // To prevent accidental execution of a command within Eclipse (and possibly
        // disastrous consequences), require confirmation. To profit from this safety net,
        // add "-ea" as a default VM argument to your Eclipse JRE definition.
        assert confirm(args);

        var cmd = cmdClass.getConstructor(String[].class)
                .newInstance(new Object[] { subarray(args, 1, args.length) });
        cmd.call();
    }

    @SuppressWarnings("resource")
    private static boolean confirm(String[] args) {
        System.out.println("About to execute " + stream(args).collect(joining(" ")));
        System.out.print("Press Enter to continue.");
        new Scanner(System.in).nextLine();
        return true;
    }
}
