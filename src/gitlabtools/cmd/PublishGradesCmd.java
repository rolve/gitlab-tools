package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.lines;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.csv.CSVFormat.TDF;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;

import org.apache.commons.csv.CSVParser;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.RepositoryFile;

import com.lexicalscope.jewel.cli.Option;

public class PublishGradesCmd extends Cmd<PublishGradesCmd.Args> {

    public PublishGradesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studProjects = getProjectsIn(getSubGroup(mainGroup, args.getSubgroupName()));

        CSVParser parser = null;
        try {
            parser = TDF.withIgnoreSurroundingSpaces(false).withHeader()
                    .parse(new FileReader(args.getGradesFile(), UTF_8));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("duplicate name")) {
                throw new RuntimeException("If you want multiple empty lines, you'll " +
                        "have to hack around this limitation using a different amount " +
                        "of spaces for each empty column...", e);
            }
        }
        var appendix = lines(Paths.get(args.getAppendixFile())).collect(joining("\n"));

        var fileApi = gitlab.getRepositoryFileApi();
        
        System.out.println("Publishing grades...");
        if (args.isDryRun()) {
            progress.mute();
        }
        for (var record : parser) {
            var builder = new StringBuilder();
            for (var col : parser.getHeaderMap().keySet()) {
                if (col.isBlank()) {
                    builder.append("\n");
                } else if (!record.get(col).isBlank()) {
                    builder.append(col + ": " + record.get(col) + "\n");
                }
            }
            builder.append("\n\n").append(appendix);

            if (args.isDryRun()) {
                System.out.println(builder + "\n\n----------------------------------\n");
                continue;
            }

            var name = record.get("NETHZ");
            var project = studProjects.stream()
                    .filter(p -> p.getName().equals(name)).findFirst();

            if (project.isPresent()) {
                var path = args.getProjectName() + "/grade.txt";
                var file = new RepositoryFile();
                file.setContent(builder.toString());
                file.setFilePath(path);
                try {
                    fileApi.createFile(file, project.get().getId(), "master",
                            "publish grades for " + args.getProjectName());
                    progress.advance();
                } catch (GitLabApiException e) {
                    if (e.getMessage().contains("already exists")) {
                        progress.advance("existing");
                    } else {
                        throw e;
                    }
                }
            } else {
                progress.advance("failed");
                progress.interrupt();
                System.out.println("Warning: no project found for " + name);
            }
        }
    }

    interface Args extends ArgsWithProjectAccess {
        @Option
        String getProjectName();

        @Option
        File getGradesFile(); // tsv, with "NETHZ" column, plus arbitrary other columns. UTF-8!

        @Option
        String getAppendixFile();

        @Option
        boolean isDryRun();
    }
}
