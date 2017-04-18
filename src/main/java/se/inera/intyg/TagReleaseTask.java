package se.inera.intyg;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class TagReleaseTask extends DefaultTask {

    public TagReleaseTask() {
        setDescription("Tags the current git head with the project's version.");
    }

    @TaskAction
    public void tagRelease() throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repo = builder.setGitDir(new File(getProject().getRootProject().getProjectDir(), ".git"))
                .readEnvironment().findGitDir().build()) {
            Git git = new Git(repo);

            String prefix = System.getProperty("intyg.tag.prefix", "v");
            git.tag().setName(prefix + getProject().getVersion()).setMessage("Release of " + getProject().getVersion()).call();

            CredentialsProvider provider = new UsernamePasswordCredentialsProvider(
                    System.getProperty("githubUser"), System.getProperty("githubPassword"));
            git.push().setCredentialsProvider(provider).setPushTags().call();
        }
    }
}
