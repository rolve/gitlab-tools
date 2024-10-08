package ch.trick17.gitlabtools.cmd;

import com.lexicalscope.jewel.cli.Option;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static ch.trick17.gitlabtools.CourseFileReader.readSimpleCourseFile;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

public abstract class CmdForProjects<A extends CmdForProjects.Args> extends Cmd<A> {

    private List<Project> projects;

    public CmdForProjects(A args) throws IOException {
        super(args);
    }

    protected final List<Project> getProjects() throws GitLabApiException, IOException {
        if (projects == null) {
            projects = getProjectsFromGitLab();
        }
        return projects;
    }

    private List<Project> getProjectsFromGitLab() throws GitLabApiException, IOException {
        var projects = gitlab.getGroupApi().getProjectsStream(args.getGroup())
                .sorted(comparing(Project::getName));
        if (args.getCourseFile() != null) {
            projects = filter(projects);
        }
        return projects.collect(toList());
    }

    private Stream<Project> filter(Stream<Project> projects) throws IOException {
        var names = Set.copyOf(readSimpleCourseFile(Path.of(args.getCourseFile())));
        return projects.filter(args.isWithProjectNamePrefix()
                ? p -> names.contains(p.getName().split("_", 2)[1])
                : p -> names.contains(p.getName()));
    }

    @Override
    protected int taskCount() throws Exception {
        return getProjects().size();
    }

    interface Args extends ch.trick17.gitlabtools.cmd.Args {

        /**
         * When specified, the command will be applied only to the
         * projects belonging to the students in the file. Note that
         * if the projects have been created with a project name prefix,
         * the {@link #isWithProjectNamePrefix()} option is required
         * for this to work. This filtering does not work for team
         * projects.
         */
        @Option(defaultToNull = true)
        String getCourseFile();

        /**
         * When {@link #getCourseFile()} is specified, this option is
         * required in case the projects have been created with a prefix.
         * Subclasses of {@link CmdForProjects} may use this option for
         * other purposes (see, e.g., {@link AssignMembersCmd}).
         */
        @Option
        boolean isWithProjectNamePrefix();
    }
}
