package ch.trick17.gitlabtools;

import ch.trick17.gitlabtools.cmd.*;
import com.lexicalscope.jewel.cli.ArgumentValidationException;
import gitlabtools.cmd.*;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static java.lang.String.join;
import static java.util.Map.entry;
import static org.apache.commons.lang3.ArrayUtils.subarray;

public class GitLabToolsCli {

    private static final Map<String, Cmd.Constructor> COMMANDS = Map.ofEntries(
            entry("create-projects", CreateProjectsCmd::new),
            entry("create-branch", CreateBranchCmd::new),
            entry("protect-branch", ProtectBranchCmd::new),
            entry("assign-members", AssignMembersCmd::new),
            entry("publish-file", PublishFileCmd::new),
            entry("publish-dir", PublishDirectoryCmd::new),
            entry("create-merge-request", CreateMergeRequestCmd::new),
            entry("extract-from-merge-requests", ExtractFromMergeRequestsCmd::new),
            entry("checkout", CheckoutCmd::new),
            entry("export-sources", ExportSourcesCmd::new),
            entry("clone", CloneCmd::new));

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

        var cmd = constructCmd(cmdName, subarray(args, 1, args.length));
        confirm(args);
        cmd.execute();
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
     * disastrous consequences), require confirmation. This can be disabled
     * by setting the environment variable GITLAB_TOOLS_SKIP_CONFIRM.
     */
    private static void confirm(String[] args) {
        if (runFromIde() && requireConfirmation()) {
            System.out.println("About to execute " + join(" ", args));
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

    private static boolean requireConfirmation() {
        return System.getenv("GITLAB_TOOLS_SKIP_CONFIRM") == null;
    }
}
