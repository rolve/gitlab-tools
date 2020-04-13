package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.*;
import static org.apache.commons.csv.CSVFormat.TDF;

import java.nio.file.Path;
import java.util.TreeSet;

import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

import csv.Column;
import csv.CsvReader;

public class CreateTeamProjectsCmd extends Cmd<CreateTeamProjectsCmd.Args> {

    public CreateTeamProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    @Override
    protected void doExecute() throws Exception {
        var group = getGroup(args.getGroupName());
        var subgroup = getSubgroup(group, args.getSubgroupName());
        var existingProjects = getProjectsIn(subgroup).stream()
                .map(Project::getName)
                .collect(toSet());

        var teams = new CsvReader<>(TDF.withHeader(), TeamMember.class)
                .readAll(Path.of(args.getTeamsFile())).stream()
                .collect(groupingBy(m -> m.team,
                        mapping(m -> m.username, toCollection(TreeSet::new))))
                .values().stream()
                .map(set -> set.stream().collect(joining("_")))
                .collect(toList());

        System.out.println("Creating projects for " + teams.size() + " teams...");
        for (var team : teams) {
            if (existingProjects.contains(team)) {
                progress.advance("existing");
            } else {
                gitlab.getProjectApi().createProject(subgroup.getId(), team);
                progress.advance();
            }
        }
    }

    public interface Args extends ArgsWithProjectAccess {
        @Option(defaultValue = "teams.txt")
        String getTeamsFile();
    }

    static class TeamMember {
        @Column("Username")
        String username;
        @Column("Team")
        String team;
    }
}
