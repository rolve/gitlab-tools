# GitLab Tools (for the Classroom) [![pipeline status](https://gitlab.com/rolve/gitlab-tools/badges/main/pipeline.svg)](https://gitlab.com/rolve/gitlab-tools/-/commits/main)

Command line tools for managing large numbers of GitLab projects, e.g., for 
teaching.

## What this does

When teaching programming, you often want students to have their own Git 
repository. These tools help you do this using
[GitLab](https://about.gitlab.com/), by allowing you to create large numbers 
of GitLab projects, assign members, publish exercise templates, and 
collect submissions, all using simple commands and text files.

To use these tools, you need a GitLab instance and a user with the 
permissions to create projects and groups. The command line tools use the
[GitLab REST API](https://docs.gitlab.com/ee/api/) and possibly
[JGit](https://www.eclipse.org/jgit/) to set up projects inside a GitLab 
group (and subgroup).


## Download

Download the latest version:
[gitlab-tools.jar](https://gitlab.com/rolve/gitlab-tools/-/jobs/artifacts/main/raw/target/gitlab-tools.jar?job=integration-test)


## How to use this

First, download the jar file above and make sure you have a JRE installed 
(Java 11 or greater). For authentication, the tools work with a 'token.txt' 
file, which must contain a
[GitLab access token](https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html)
with `api` scope. If no such file is found, the program can create a
suitable access token for you. You'll need to provide your GitLab username 
and password for this one time.

Note that all commands can be executed repeatedly without changing 
previously created or published things (they are [idempotent](https://en.wikipedia.org/wiki/Idempotence)).
This is handy if students join later; in that case, just add them to the 
course file (see below) and run previously executed commands again. The 
commands detect existing projects, members, and published templates and skip 
these.

### Create individual projects

To create a project for each individual student, prepare a text file 
(the "course file") that contains all GitLab usernames of the students, one
per line. Then, execute the following command:

    java -jar gitlab-tools.jar create-projects \
        --gitlabUrl https://your-gitlab-instance.org \
        --groupName "Your GitLab Group" \
        --subgroupName "Your Subgroup" \
        --courseFile your-course-file.txt

This creates one project for each user in the course file, with a name 
that matches the username. Optionally, you may provide a prefix that is 
prepended to each project name (using an underscore as separator):

    java -jar gitlab-tools.jar create-projects \
        ...
        --projectNamePrefix programming-exercises

This would create project names like `programming-exercises_john.doe`.

Note that some options have default values:
* `--gitlabUrl`: `https://gitlab.fhnw.ch` (I'm lazy)
* `--subgroupName`: `exercises`
* `--courseFile`: `course.txt`

### Create team projects

To create projects for teams of students, append `--teamProjects`:

    java -jar gitlab-tools.jar create-projects \
        ...
        --teamProjects

In this case, each line in the course file needs a username and a team name
(or number), *separated by a single tab*. The command will group all users 
with the same team name and create a single project for the team, 
concatenating all usernames (and possibly prepending a prefix).

For example, given the following course file,

    john.doe	1
    lisa.loe	1
    mike.moe	2
    sara.soe	2
    jack.joe	3

the command will create three projects, named `john.doe_lisa.loe`,
`mike.moe_sara.soe` and `jack.joe` (who is working alone). The 
order of the usernames within a project name is alphabetical.

### Assign members

The commands above only *create* the projects, but don't assign any members 
yet. This is useful if you want to push some template code beforehand (see 
below). To give students access to their repositories, use the following 
command:

    java -jar gitlab-tools.jar assign-members \
        --gitlabUrl https://your-gitlab-instance.org \
        --groupName "Your GitLab Group" \
        --subgroupName "Your Subgroup" \

Note that this command does not require a course file. Instead, it 
determines the users to add as members based on the names of the existing 
projects in the given subgroup. If you used a project name prefix with 
`create-projects`, let the command know about it:

    java -jar gitlab-tools.jar assign-members \
        ...
        --withProjectNamePrefix

If you created team projects, specify this too:

    java -jar gitlab-tools.jar assign-members \
        ...
        --teamProjects

### Publish templates

The following command allows you to publish code templates (or any files, 
actually) to all projects in a GitLab subgroup. It clones each of the 
projects, copies a given directory containing the template files, commits, and 
pushes the changes back to GitLab.

    java -jar gitlab-tools.jar publish-template \
        --gitlabUrl https://your-gitlab-instance.org \
        --groupName "Your GitLab Group" \
        --subgroupName "Your Subgroup" \
        --templateDir path-to-template-dir
        --destDir dir-within-repo

Here, `path-to-template-dir` should point to the local directory that 
contains the files that you want to publish; `dir-within-repo` is the name 
(or relative path) of the directory inside the repository in which the 
template files will be placed. If you omit `--destDir`, the contents of the 
template directory will be placed directly into the root directory of the 
repository.

### Further commands and help

Execute the jar file without specifying a command to list further available 
commands (not documented yet):

    java -jar gitlab-tools.jar

Each command has a `--help` option that lists available options. For example,

    java -jar gitlab-tools.jar clone --help

produces something like the following:

    The options available are:
        [--defaultBranch value]
        --destinationDir value
        [--gitlabUrl value]
        --groupName value
        [--help]
        [--subgroupName value]
        [--tokenFile value]
