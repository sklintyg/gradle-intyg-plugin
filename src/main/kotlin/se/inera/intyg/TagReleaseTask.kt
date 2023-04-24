package se.inera.intyg

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.io.File

open class TagReleaseTask : DefaultTask() {
    init {
        description = "Tags the current git head with the project's version."
    }

    @TaskAction
    fun tagRelease() {
        FileRepositoryBuilder().setGitDir(File(project.rootProject.projectDir, ".git")).readEnvironment().findGitDir().build().use {
            val git = Git(it)
            git.tag().setName(System.getProperty("intyg.tag.prefix", "v") + project.version).
                    setMessage("Release of ${project.version}").call()
            git.push().setCredentialsProvider(UsernamePasswordCredentialsProvider(
                    System.getProperty("githubUser"), System.getProperty("githubPassword"))).setPushAll().setPushTags().call()
        }
    }
}
