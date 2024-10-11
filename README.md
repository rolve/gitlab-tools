# GitLab Tools (for the Classroom) [![pipeline status](https://gitlab.fhnw.ch/gitlab-tools/gitlab-tools/badges/main/pipeline.svg)](https://gitlab.fhnw.ch/gitlab-tools/gitlab-tools/-/commits/main)

Command line tools for managing large numbers of GitLab projects, e.g., for teaching.

## What this does

When teaching programming, you often want students to have their own Git repository. These tools help you do this using [GitLab](https://about.gitlab.com/), by allowing you to create large numbers  of GitLab projects, assign members, publish directories and files as exercise templates, and collect submissions, all using simple commands and text files.

To use these tools, you need a GitLab instance and a user with the  permissions to create projects and groups. The command line tools use the [GitLab REST API](https://docs.gitlab.com/ee/api/) and possibly [JGit](https://www.eclipse.org/jgit/) to set up projects inside a GitLab group.


## Download

Download the latest version: [gitlab-tools.jar](https://gitlab.fhnw.ch/gitlab-tools/gitlab-tools/-/jobs/artifacts/main/raw/target/gitlab-tools.jar?job=deploy)


## How to use this

First, download the jar file above and make sure you have a JRE installed (Java 17 or greater). For authentication, the tools work with a 'token.txt' file, which must contain a [GitLab access token](https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html) with `api` scope. ~~If no such file is found, the program can create a suitable access token for you. You'll need to provide your GitLab username and password for this one time.~~ (This is currently broken for newer versions of GitLab.)

Note that all commands can be executed repeatedly without changing previously created or published things (they are _[idempotent](https://en.wikipedia.org/wiki/Idempotence)_). This is handy if students join later; in that case, just add them to the course file (see below) and run previously executed commands again. The commands detect existing projects, members, and published files and directories and skip these.

### Create individual projects

To create a project for each individual student, prepare a text file (the "course file") that contains all GitLab usernames of the students, one per line. Then, execute the following command:

    java -jar gitlab-tools.jar create-projects \
        --gitlabUrl https://your-gitlab-instance.org \
        --group path/of/gitlab/group \
        --courseFile your-course-file.txt

This creates one project for each user in the course file, with a name that matches the username. Optionally, you may provide a prefix that is prepended to each project name (using an underscore as separator):

    java -jar gitlab-tools.jar create-projects \
        ...
        --projectNamePrefix programming-exercises

This would create project names like `programming-exercises_john.doe`.

Note that some options have default values:
* `--gitlabUrl`: `https://gitlab.fhnw.ch` (I'm lazy)
* `--courseFile`: `course.txt`

### Create team projects

To create projects for teams of students, append `--teamProjects`:

    java -jar gitlab-tools.jar create-projects \
        ...
        --teamProjects

In this case, each line in the course file needs a username and a team name (or number), *separated by a single tab*. The command will group all users with the same team name and create a single project for the team, concatenating all usernames (and possibly prepending a prefix).

For example, given the following course file,

    john.doe	1
    lisa.loe	1
    mike.moe	2
    sara.soe	2
    jack.joe	3

the command will create three projects, named `john.doe_lisa.loe`, `mike.moe_sara.soe` and `jack.joe` (who is working alone). The order of the usernames within a project name is alphabetical.

### Assign members

The commands above only *create* the projects, but don't assign any members yet. This is useful if you want to push some template code beforehand (see below). To give students access to their repositories, use the following command:

    java -jar gitlab-tools.jar assign-members \
        --gitlabUrl https://your-gitlab-instance.org \
        --group path/of/gitlab/group


Note that this command does not require a course file. Instead, it determines the users to add as members based on the names of the existing projects in the given group. If you used a project name prefix with `create-projects`, let the command know about it:

    java -jar gitlab-tools.jar assign-members \
        ...
        --withProjectNamePrefix

If you created team projects, specify this too:

    java -jar gitlab-tools.jar assign-members \
        ...
        --teamProjects

### Publish files

There are two commands to publish files into the repositories inside a GitLab group. They can only be used to publish _new files_, not overwrite existing ones (with one exception). This limitation is intentional: First, it prevents accidentally overwriting the students' work; second, it makes it more efficient to check which repositories have already been processed when executing the commands repeatedly (see above).

The following command allows you to publish the contents of a given directory (e.g., a code template for a programming exercise) to all repositories in a GitLab group. The command clones each of the repositories, copies the files, commits, and pushes the changes back to GitLab.

    java -jar gitlab-tools.jar publish-dir \
        --gitlabUrl https://your-gitlab-instance.org \
        --group path/of/gitlab/group \
        --dir path-to-dir
        --destDir dir-within-repo

Here, `path-to-dir` should point to the local directory that contains the files that you want to publish; `dir-within-repo` is the relative path of the directory that will be created inside the repository and into which the files will be copied. Any repository that already contains a directory (or file) with that path is skipped. If `--destDir` is omitted, the contents of the local directory will be copied into the _root directory_ of the repository. Any non-empty repository is skipped. (A repository is considered non-empty if it contains any file except for a `README.md`, which is possibly overwritten if present.)

To publish a single file, use the following command instead:

    java -jar gitlab-tools.jar publish-file \
        --gitlabUrl https://your-gitlab-instance.org \
        --group path/of/gitlab/group \
        --file path-to-file
        --destDir dir-within-repo

Again, `dir-within-repo` is the directory in which the file will be copied; if omitted, the file will be copied directly into the root directory of the repository. Any repository that already contains a file with the given path is skipped.

### Further commands and help

Execute the jar file without specifying a command to list further available commands (not documented yet):

    java -jar gitlab-tools.jar

Each command has a `--help` option that lists available options. For example,

    java -jar gitlab-tools.jar checkout --help

produces something like the following:

    The options available are:
        [--branch value]
        [--courseFile value]
        --destDir value
        [--gitLabUrl value]
        --group value
        [--help]
        [--tokenFile value]
        [--withProjectNamePrefix]
