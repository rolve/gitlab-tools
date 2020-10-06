package gitlabtools.cmd;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.*;
import static java.util.Arrays.asList;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.csv.CSVFormat.TDF;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.lib.FileMode.GITLINK;
import static org.eclipse.jgit.lib.ObjectId.fromString;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;
import gitlabtools.GroupStudent;
import gitlabtools.ProgressTracker;

public class CreateRoomProjectsCmd extends Cmd<CreateRoomProjectsCmd.Args> {

    public CreateRoomProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    protected void doExecute() throws Exception {
        var group = getGroup(args.getGroupName());
        var roomsSubgroup = getSubgroup(group, args.getRoomsSubgroupName());

        var students = new CsvReader<>(TDF.withHeader(), GroupStudent.class)
                .readAll(Path.of(args.getGroupsFile()));

        for (var student : students) {
            if (student.username.isBlank()) {
                throw new RuntimeException("invalid username for " + student.firstName + " " + student.lastName);
            }
        }

        var projects = getProjects(args);

        System.out.print("Fetching commit hashes...");
        var fetchProgress = new ProgressTracker();
        for (var student : students) {
            var project = projects.stream()
                    .filter(p -> p.getName().equals(student.username))
                    .findFirst();
            if (project.isEmpty()) {
                throw new RuntimeException("No project found for " + student.username);
            }
            var master = gitlab.getRepositoryApi().getBranch(project.get().getId(), "master");
            student.commitHash = master.getCommit().getId();
            fetchProgress.advance();
        }
        fetchProgress.printSummary();

        var credentials = new UsernamePasswordCredentialsProvider("", token);

        var rooms = students.stream().map(s -> s.room).collect(toSet());
        System.out.print("Creating " + rooms.size() + " room projects...");
        for (var room : rooms) {
            var roomStudents = students.stream()
                    .filter(s -> s.room.equals(room)).collect(toList());

            var safeRoom = room.replace(' ', '-');
            var project = gitlab.getProjectApi().createProject(roomsSubgroup.getId(), safeRoom);

            var repoDir = createTempDirectory("gitlab-tools");
            try {
                Git git = cloneRepository()
                        .setURI(project.getWebUrl())
                        .setDirectory(repoDir.toFile())
                        .setCredentialsProvider(credentials)
                        .call();

                write(repoDir.resolve(".gitmodules"), generateModules(roomStudents));

                var repo = git.getRepository();
                var editor = repo.lockDirCache().editor();
                for (var student : roomStudents) {
                    editor.add(new PathEdit(student.username) {
                        public void apply(DirCacheEntry ent) {
                            ent.setFileMode(GITLINK);
                            ent.setObjectId(fromString(student.commitHash));
                        }
                    });
                }
                editor.commit();

                git.add()
                        .addFilepattern(".")
                        .call();
                git.commit()
                        .setMessage("Create room repo")
                        .call();
                git.push()
                        .add("master")
                        .setCredentialsProvider(credentials)
                        .call();
                git.close();
                progress.advance();
            } finally {
                walk(repoDir).sorted(reverseOrder())
                        .map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private List<String> generateModules(List<GroupStudent> students) {
        var entry = asList(
                "[submodule \"STUDENT\"]",
                "    path = STUDENT",
                "    url = https://gitlab.inf.ethz.ch/" + args.getGroupName() + "/students/STUDENT.git",
                "    branch = master");
        return students.stream()
                .flatMap(s -> entry.stream().map(l -> l.replace("STUDENT", s.username)))
                .collect(toList());
    }

    public interface Args extends ArgsWithProjectAccess {
        @Option(defaultValue = "rooms")
        String getRoomsSubgroupName();

        @Option(defaultValue = "groups.txt") // tab-separated
        String getGroupsFile();
    }
}