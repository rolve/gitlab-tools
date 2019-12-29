import static java.util.Arrays.stream;
import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.ArrayUtils.subarray;

import java.util.Map;
import java.util.Scanner;

public class GitlabToolsCli {

    private static Map<String, Class<? extends Cmd<?>>> commands = Map.ofEntries(
        entry("create-issues", CreateIssuesCmd.class),
        entry("create-projects", CreateProjectsCmd.class),
        entry("create-room-projects", CreateRoomProjectsCmd.class),
        entry("assign-members", AssignMembersCmd.class),
        entry("publish-fast", PublishFastCmd.class),
        entry("publish-grades", PublishGradesCmd.class),
        entry("checkout-submissions", CheckoutSubmissionsCmd.class),
        entry("export-sources", ExportSourcesCmd.class),
        entry("test-student-data", TestStudentDataCmd.class),
        entry("protect-master", ProtectMasterCmd.class),
        entry("submission-stats", SubmissionStatsCmd.class),
        entry("clone", CloneCmd.class));

    public static void main(String[] args) throws Exception {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");

        if (args.length == 0) {
            System.err.println("no command specified");
            return;
        }

        var cmdClass = commands.get(args[0]);
        if (cmdClass == null) {
            System.err.println("unknown command " + args[0]);
        }

        // To prevent accidental execution of a command within Eclipse (and possibly
        // disastrous consequences), require confirmation.
        confirm(args);

        var cmd = cmdClass.getConstructor(String[].class)
                .newInstance(new Object[] { subarray(args, 1, args.length) });
        cmd.execute();
    }

    @SuppressWarnings("resource")
    private static boolean confirm(String[] args) {
        System.out.println("About to execute " + stream(args).collect(joining(" ")));
        System.out.print("Press Enter to continue.");
        new Scanner(System.in).nextLine();
        return true;
    }
}
