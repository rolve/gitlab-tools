# GitLab Tools

Command line tools for managing large numbers of GitLab projects.

## What this does

When teaching programming, you often want students to have their own Git 
repository. These tools helps you do this using
[GitLab](https://about.gitlab.com/). They allow you to create a large number 
of GitLab projects, assign members, distribute exercise templates, and 
collect submissions, all using simple commands and text files.

To use these tools, you need a GitLab instance and a user with the 
permissions to create projects and groups. The command line tools use the
[GitLab REST API](https://docs.gitlab.com/ee/api/) and possibly
[JGit](https://www.eclipse.org/jgit/) to set up projects inside a GitLab group.


## Download

Download the latest version:
[gitlab-tools.jar](https://gitlab.com/rolve/gitlab-tools/-/jobs/artifacts/main/raw/target/gitlab-tools.jar?job=test)


## How to use this

Fist, download the jar file above and make sure you have a JRE installed 
(Java 11 or greater). For authentication, the tools require a 'token.txt' 
file that contains a
[GitLab access token](https://docs.gitlab.com/ee/user/profile/personal_access_tokens.html)
with `api` scope. Place that file next to the 'gitlab-tools.jar' file.

### Create individual projects

To create a project for each student separately, prepare a text file 
(the "course file") that contains all GitLab usernames of the students, one
per line. Then, execute the following command:

    java -jar gitlab-tools.jar create-projects \
        --gitlabUrl https://your-gitlab-instance.org \
        --groupName "Your GitLab Group" \
        --subgroupName "Your Subgroup" \
        --courseFile your-course-file.txt

This creates one repository for each user in the course file, with a name 
that matches the username. Optionally, you may provide a prefix that is 
prepended to each project name (using an underscore as separator):

    java -jar gitlab-tools.jar create-projects \
        ...
        --projectNamePrefix programming-exercises

This would create project names like "programming-exercises_john.doe".

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
or numbers, *separated by a single tab*. The command will group all users 
with the same team name and create a single project for the team, 
concatenating all username (and possibly prepending a prefix).

For example, given the following course file,

    john.doe	1
    lisa.loe	1
    mike.moe	2
    sara.soe	2
    jack.joe	3

the command will create three projects, named `john.doe_lisa.loe`,
`mike.moe_sara.soe` and `jack.joe` (who is working alone). The order of the 
usernames within a project name is alphabetical.

### Assign members

The commands above only *create* the projects, but don't assign any members 
yet. This is useful if you want to push a some template code beforehand (see 
below). To give students access to their repositories, use the following 
command:

    java -jar gitlab-tools.jar assign-members \
        --gitlabUrl https://your-gitlab-instance.org \
        --groupName "Your GitLab Group" \
        --subgroupName "Your Subgroup" \

Note that this command does not require a course file. Instead, it 
determines the users to add as members based on the names of the existing 
projects in the given subgroup. If you used a project name prefix, specify
this:

    java -jar gitlab-tools.jar assign-members \
        ...
        --withProjectNamePrefix

If you created team projects, specify this too:

    java -jar gitlab-tools.jar assign-members \
        ...
        --teamProjects

### Publishing templates

TODO
