package gitlabtools;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import gitlabtools.cmd.*;

import java.lang.management.ManagementFactory;
import java.util.*;

import static java.util.Arrays.stream;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ArrayUtils.*;

public class GitLabToolsCli {

    private static final Map<String, Cmd.Constructor> COMMANDS = Map.of(
            "create-projects", CreateProjectsCmd::new,
            "create-branch", CreateBranchCmd::new,
            "protect-branch", ProtectBranchCmd::new,
            "assign-members", AssignMembersCmd::new,
            "publish-template", PublishTemplateCmd::new,
            "create-merge-request", CreateMergeRequestCmd::new,
            "checkout-submissions", CheckoutSubmissionsCmd::new,
            "export-sources", ExportSourcesCmd::new,
            "clone", CloneCmd::new);

    public static void main(String[] args) throws Exception {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");

        if (args.length == 0) {
            System.err.println("No command specified. Available commands: ");
            COMMANDS.keySet().stream()
                    .sorted()
                    .map("  "::concat)
                    .forEach(System.err::println);
            return;
        }
        var cmdName = args[0];
        if (!COMMANDS.containsKey(cmdName)) {
            System.err.println("Unknown command " + cmdName);
            return;
        }

        int batchIndex = indexOf(args, "--batch");
        if (batchIndex == INDEX_NOT_FOUND) {
            var cmdArgs = subarray(args, 1, args.length);
            var cmd = constructCmd(cmdName, cmdArgs);
            confirm(cmdName, List.<String[]>of(cmdArgs));
            cmd.execute();
        } else {
            var constArgs = subarray(args, 1, batchIndex);
            var varArgs = subarray(args, batchIndex + 1, args.length);
            var completeArgs = stream(varArgs)
                    .map(a -> addAll(constArgs, split(a)))
                    .collect(toList());
            var cmds = new ArrayList<Cmd<?>>();
            for (var cmdArgs : completeArgs) {
                cmds.add(constructCmd(cmdName, cmdArgs));
            }
            confirm(cmdName, completeArgs);
            for (var cmd : cmds) {
                cmd.execute();
            }
        }
    }

    private static Cmd<?> constructCmd(String name, String[] args) throws Exception {
        try {
            return COMMANDS.get(name).construct(args);
        } catch (ArgumentValidationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return null;
        }
    }

    /**
     * To prevent accidental execution of a command within an IDE (and possibly
     * disastrous consequences), require confirmation.
     */
    private static void confirm(String cmd, List<String[]> argsList) {
        if (runFromIde()) {
            System.out.print("About to execute" + (argsList.size() == 1 ? " " : "\n"));
            for (var args : argsList) {
                System.out.println(cmd + " " + String.join(" ", args));
            }
            System.out.print("Press Enter to continue.");
            try {
                new Scanner(System.in).nextLine();
            } catch (NoSuchElementException e) {
                System.exit(0);
            }
        }
    }

    private static boolean runFromIde() {
        // assuming IDE attaches a Java agent (IntelliJ does...)
        var args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        return args.stream().anyMatch(a -> a.startsWith("-javaagent:"));
    }

    private static String[] split(String args) {
        var doubleQuot = "\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"";
        var singleQuot = "'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)'";
        var spaceless = "[^\\s]+";
        var regex = compile(doubleQuot + "|" + singleQuot + "|" + spaceless);
        var matcher = regex.matcher(args);
        var list = new ArrayList<String>();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                list.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                list.add(matcher.group(2));
            } else {
                list.add(matcher.group());
            }
        }
        return list.toArray(String[]::new);
    }
}
