package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.util.stream.Collectors.*;
import static org.apache.commons.csv.CSVFormat.TDF;

import java.nio.file.Path;
import java.util.TreeSet;

import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Project;

import com.lexicalscope.jewel.cli.Option;

import csv.Column;
import csv.CsvReader;

public class CreateTeamProjectsCmd extends Cmd<CreateTeamProjectsCmd.Args> {

    private final AccessLevel access;

    public CreateTeamProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
        access = AccessLevel.valueOf(args.getMasterBranchAccess().toUpperCase());
    }

    @Override
    protected void doExecute() throws Exception {
        var branchApi = gitlab.getProtectedBranchesApi();

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
                .map(set -> String.join("_", set))
                .collect(toList());

        System.out.println("Creating projects for " + teams.size() + " teams...");
        for (var team : teams) {
            if (args.getProjectNamePrefix() != null) {
                if (args.getProjectNamePrefix().contains("_")) {
                    throw new AssertionError("illegal prefix; must not contain _");
                }
                team = args.getProjectNamePrefix() + "_" + team;
            }

            if (existingProjects.contains(team)) {
                progress.advance("existing");
            } else {
                var project = gitlab.getProjectApi().createProject(subgroup.getId(), team);

                // remove all protected branches first
                var branches = branchApi.getProtectedBranches(project.getId());
                for (var branch : branches) {
                    branchApi.unprotectBranch(project.getId(), branch.getName());
                }

                // then protect 'master' from force-pushing
                branchApi.protectBranch(project.getId(), "master", access, access);

                progress.advance();
            }
        }
    }

    public interface Args extends gitlabtools.cmd.Args {
        @Option(defaultValue = "teams.txt")
        String getTeamsFile();

        @Option(defaultValue = "developer", pattern = "developer|maintainer|admin")
        String getMasterBranchAccess();

        @Option(defaultToNull = true)
        String getProjectNamePrefix();
    }

    static class TeamMember {
        @Column("Username")
        String username;
        @Column("Team")
        String team;
    }
}
