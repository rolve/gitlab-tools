import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.util.Arrays.asList;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.csv.CSVFormat.EXCEL;
import static org.eclipse.jgit.api.Git.cloneRepository;
import static org.eclipse.jgit.lib.FileMode.GITLINK;
import static org.eclipse.jgit.lib.ObjectId.fromString;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.lexicalscope.jewel.cli.Option;

import csv.Column;
import csv.CsvReader;

public class CreateRoomProjectsCmd extends Cmd<CreateRoomProjectsCmd.Args> {

    public CreateRoomProjectsCmd(String[] rawArgs) throws Exception {
        super(createCli(Args.class).parseArguments(rawArgs));
    }

    void call() throws Exception {
        var mainGroup = getGroup(args.getGroupName());
        var studGroup = getSubGroup(mainGroup, "students");
        var roomGroup = getSubGroup(mainGroup, "rooms");

        var students = new CsvReader(EXCEL.withHeader())
                .read(Paths.get(args.getGroupsFile()), Student.class);

        for (Student student : students) {
            if (!student.email.endsWith("@student.ethz.ch")) {
                throw new RuntimeException("invalid email: " + student.email);
            }
        }

        System.out.println("Fetching student projects...");
        var studentProjects = getProjectsIn(studGroup);
        System.out.println("Done.");

        System.out.print("Fetching commit hashes...");
        for (Student student : students) {
            var project = studentProjects.stream()
                    .filter(p -> p.getName().equals(student.nethz()))
                    .findFirst().get();
            var master = gitlab.getRepositoryApi().getBranch(project.getId(), "master");
            student.commitHash = master.getCommit().getId();
            System.out.print(".");
        }
        System.out.println("\nDone.");

        var credentials = new UsernamePasswordCredentialsProvider("", token);

        System.out.print("Creating repos...");
        var rooms = students.stream().map(s -> s.room).collect(toSet());
        for (String room : rooms) {
            var roomStudents = students.stream()
                    .filter(s -> s.room.equals(room)).collect(toList());

            var safeRoom = room.replace(' ', '-');
            var project = gitlab.getProjectApi().createProject(roomGroup.getId(), safeRoom);

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
                for (Student student : roomStudents) {
                    editor.add(new PathEdit(student.nethz()) {
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
                System.out.print(".");
            } finally {
                walk(repoDir).sorted(reverseOrder())
                        .map(Path::toFile).forEach(File::delete);
            }
        }
        System.out.println("\nDone.");
    }

    private List<String> generateModules(List<Student> students) {
        var entry = asList(
                "[submodule \"STUDENT\"]",
                "    path = STUDENT",
                "    url = https://gitlab.inf.ethz.ch/" + args.getGroupName() + "/students/STUDENT.git",
                "    branch = master");
        return students.stream()
                .flatMap(s -> entry.stream().map(l -> l.replace("STUDENT", s.nethz())))
                .collect(toList());
    }

    static class Student {
        @Column("Raumzeit")
        String room;
        @Column("Rufname")
        String firstName;
        @Column("Nachname")
        String lastName;
        @Column("E-Mail")
        String email;

        String commitHash;

        String nethz() { return email.split("@")[0]; }
    }

    public interface Args extends Cmd.Args {
        @Option
        String getGroupName();

        @Option(defaultValue = "groups.txt")    // comma-separated, make sure contains
        String getGroupsFile();                 // only @student.ethz.ch addresses!
    }
}
