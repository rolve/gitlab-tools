package ch.trick17.gitlabtools.cmd;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;

import java.io.IOException;

import static org.eclipse.jgit.api.MergeCommand.FastForwardMode.FF;

final class GitUtils {

    /**
     * Helper method to check out the remote branch with the given name. If
     * there already exists a local branch with that name, it is checked out and
     * fast-forwarded to the remote branch, if necessary. Otherwise, it is
     * created with the remote branch as the start point. In both cases, the
     * remote branch must already exist, i.e., any fetching must be done before
     * calling this method.
     */
    static void checkOutRemoteBranch(Git git, String branch) throws GitAPIException, IOException {
        // apparently, there is no cleaner way to do this...
        var create = git.branchList().call().stream()
                .map(Ref::getName)
                .noneMatch(("refs/heads/" + branch)::equals);
        if (create) {
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setStartPoint("origin/" + branch)
                    .call();
        } else {
            git.checkout()
                    .setName(branch)
                    .call();
            git.merge()
                    .include(git.getRepository().findRef("origin/" + branch))
                    .setFastForward(FF)
                    .call();
        }
    }
}
