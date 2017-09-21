package se.inera.intyg

import org.eclipse.jgit.lib.Constants.HEAD
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DateFormat
import java.util.Date
import java.util.Properties

open class VersionPropertyFileTask : DefaultTask() {
    private val projectVersion = project.version.toString()

    private val propertyFile = "${project.buildDir.path}/resources/main/version.properties"
    private val buildNumber = System.getenv("BUILD_NUMBER") ?: ""
    private val buildTime = Date()
    private var gitCommit: String? = null
    private var gitBranch: String? = null

    init {
        description = "Create a version property file from current build properties."

        FileRepositoryBuilder().setGitDir(File(project.rootProject.projectDir, ".git"))
                .readEnvironment().findGitDir().build().use { repo ->
            gitCommit = repo.findRef(HEAD).name
            gitBranch = repo.branch
        }

        inputs.property("project.version", projectVersion)
        inputs.property("gitCommit", gitCommit)
        inputs.property("gitBranch", gitBranch)
        inputs.property("buildNumber", buildNumber)
        outputs.file(propertyFile)
    }

    @TaskAction
    fun createPropertyFile() {
        val properties = Properties()
        properties.setProperty("project.version", projectVersion)
        properties.setProperty("gitCommit", gitCommit)
        properties.setProperty("gitBranch", gitBranch)
        properties.setProperty("buildNumber", buildNumber)
        properties.setProperty("buildTime", DateFormat.getDateTimeInstance().format(buildTime))

        Files.createDirectories(Paths.get(propertyFile).parent)
        FileOutputStream(propertyFile).use { fos -> properties.store(fos, "") }
    }

}
