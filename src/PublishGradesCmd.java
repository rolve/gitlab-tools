import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.lines;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;

import org.apache.commons.csv.CSVFormat;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.RepositoryFile;

import com.lexicalscope.jewel.cli.Option;

public class PublishGradesCmd extends Cmd<PublishGradesCmd.Args> {

    public PublishGradesCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studProjects = getProjectsIn(getSubGroup(mainGroup, "students"));

        var parser = CSVFormat.newFormat(';').withHeader()
                .parse(new FileReader(args.getGradesFile()));
        var appendix = lines(Paths.get(args.getAppendixFile()))
                .collect(joining("\n"));

        var fileApi = gitlab.getRepositoryFileApi();

        int created = 0;
        int existing = 0;
        for (var record : parser) {
            var builder = new StringBuilder();
            for (var col : parser.getHeaderMap().keySet()) {
                builder.append(col + (col.isEmpty() ? "" : ": ") + record.get(col)).append("\n");
            }
            builder.append("\n").append(appendix);
            
            var name = record.get("Name");
            var project = studProjects.stream()
                    .filter(p -> p.getName().equals(name)).findFirst().get();

            var path = args.getProjectName() + "/grade.txt";
            var file = new RepositoryFile();
            file.setContent(builder.toString());
            file.setFilePath(path);
            try {
                fileApi.createFile(file, project.getId(), "master", "publish grades for " + args.getProjectName());
                created++;
            } catch (GitLabApiException e) {
                if (e.getMessage().contains("already exists")) {
                    existing++;
                } else {
                    throw e;
                }
            }
            if ((existing + created) % 10 == 0) {
                System.out.printf("%d processed\n", existing + created);
                Thread.sleep(3000);
            } else {
            	Thread.sleep(500);
            }
        }
        System.out.printf("Done. %d published, %d already exist.\n", created, existing);
    }

    interface Args extends Cmd.Args {
        @Option
        String getGroupName();
        
        @Option
        String getProjectName();

        @Option
        File getGradesFile();

        @Option
        String getAppendixFile();
    }
}
