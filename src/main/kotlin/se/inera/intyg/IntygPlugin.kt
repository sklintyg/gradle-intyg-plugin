package se.inera.intyg

import com.github.spotbugs.SpotBugsExtension
import com.github.spotbugs.SpotBugsPlugin
import com.github.spotbugs.SpotBugsTask
import net.ltgt.gradle.errorprone.ErrorProneBasePlugin
import nl.javadude.gradle.plugins.license.License
import nl.javadude.gradle.plugins.license.LicenseExtension
import nl.javadude.gradle.plugins.license.LicensePlugin
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.owasp.dependencycheck.gradle.DependencyCheckPlugin
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

val PLUGIN_NAME = "se.inera.intyg.plugin.common"
val CODE_QUALITY_FLAG = "codeQuality"
val SPOTBUGS_EXCLUDE = "spotbugsExclude"
val ERRORPRONE_EXCLUDE = "errorproneExclude"

class IntygPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.quiet("Applying $PLUGIN_NAME to project ${project.name}.")

        project.pluginManager.apply(JavaPlugin::class.java)

        applyGitHooks(project)
        applyCheckstyle(project)
        applySpotbugs(project)
        applyErrorprone(project)
        applyLicence(project)
        applyJacoco(project)
        applySonar(project)
        applySharedTestReport(project)
        applyVersionPropertyFile(project)
        applyOwasp(project)

        addGlobalTaskType(project, TagReleaseTask::class.java)
        addGlobalTaskType(project, VersionPropertyFileTask::class.java)
    }

    private fun applyGitHooks(project: Project) {
        if (isRootProject(project)) {
            val repository = findGitRepository(project.rootProject.projectDir);
            val commitMsg = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "git_hooks/commit-msg")
            val preCommit = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "git_hooks/pre-commit")

            val toDir = Paths.get(repository.directory.path, "hooks")

            if (!Files.exists(toDir)) {
                Files.createDirectory(toDir)
            }

            copyFile(commitMsg.asFile(), toDir)
            copyFile(preCommit.asFile(), toDir)
        }
    }

    private fun applyCheckstyle(project: Project) {
        project.pluginManager.apply(CheckstylePlugin::class.java)

        with(project.extensions.getByType(CheckstyleExtension::class.java)) {
            config = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "/checkstyle/checkstyle.xml")
            configProperties = mapOf("package_name" to project.rootProject.name)
            isIgnoreFailures = false
            isShowViolations = true
        }

        project.afterEvaluate {
            it.getTasksByName("checkstyleMain", false).forEach { task ->
                val csTask = task as Checkstyle
                csTask.setSource("src/main/java") // Explicitly disable generated code
                csTask.onlyIf { project.hasProperty(CODE_QUALITY_FLAG) }
            }
            it.getTasksByName("checkstyleTest", false).forEach { task -> task.enabled = false }
        }
    }

    private fun applySpotbugs(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useSpotbugs(project)) {
            project.pluginManager.apply(SpotBugsPlugin::class.java)

            with(project.extensions.getByType(SpotBugsExtension::class.java)) {
                includeFilterConfig = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "/spotbugs/spotbugsIncludeFilter.xml")
                excludeFilterConfig = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "/spotbugs/spotbugsExcludeFilter.xml")
                isIgnoreFailures = false
                effort = "max"
                reportLevel = "low"
                sourceSets = setOf(project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main"))
            }

            project.afterEvaluate {
                it.tasks.withType(SpotBugsTask::class.java).forEach { task ->
                    task.classes = task.classes.filter { clazz ->
                        // There are sometimes text files in the build directory and these can obviously not be examined as java classes.
                        !clazz.path.matches(".*\\.(xml|sql|json|log|txt|ANS|properties)\\$".toRegex())
                    }
                    task.reports {
                        it.xml.isEnabled = false
                        it.html.isEnabled = true
                    }
                }
            }

        }
    }

    private fun applyErrorprone(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useErrorprone(project)) {
            project.pluginManager.apply(ErrorProneBasePlugin::class.java)

            project.afterEvaluate {
                it.getTasksByName("compileJava", false).forEach {
                    val jTask = it as JavaCompile
                    jTask.toolChain = net.ltgt.gradle.errorprone.ErrorProneToolChain.create(project)
                    jTask.options.compilerArgs.addAll(listOf(
                        "-XepExcludedPaths:.*AutoValue_.+",
                        "-Xep:BoxedPrimitiveConstructor:ERROR", "-Xep:ClassCanBeStatic:ERROR",
                        "-Xep:DefaultCharset:ERROR", "-Xep:Finally:ERROR", "-Xep:FunctionalInterfaceClash:ERROR",
                        "-Xep:ImmutableEnumChecker:ERROR", "-Xep:MissingCasesInEnumSwitch:ERROR",
                        "-Xep:MissingOverride:ERROR", "-Xep:NarrowingCompoundAssignment:ERROR",
                        "-Xep:NonOverridingEquals:ERROR", "-Xep:TypeParameterUnusedInFormals:ERROR",
                        "-Xep:TypeParameterUnusedInFormals:ERROR", "-Xep:UnnecessaryDefaultInEnumSwitch:WARN",
                        "-Xep:JavaTimeDefaultTimeZone:OFF"))
                }
            }
        }
    }

    private fun applyLicence(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG)) {
            project.pluginManager.apply(LicensePlugin::class.java)

            with(project.extensions.getByType(LicenseExtension::class.java)) {
                strictCheck = true
                header = null
                headerURI = IntygPlugin::class.java.getResource("/license/header.txt").toURI()
                includePatterns = setOf("**/*.java", "**/*.groovy", "**/*.js")
                mapping("java", "SLASHSTAR_STYLE")
                mapping("groovy", "SLASHSTAR_STYLE")
                mapping("js", "SLASHSTAR_STYLE")
            }

            project.afterEvaluate {
                it.tasks.withType(License::class.java).forEach { task ->
                    task.inheritedProperties = mutableMapOf()
                    task.inheritedProperties.put("project_name", "sklintyg")
                    task.inheritedProperties.put("project_url", "https://github.com/sklintyg")
                    task.inheritedProperties.put("year", Calendar.getInstance().get(Calendar.YEAR).toString())
                }
            }
        }
    }

    private fun applyJacoco(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG)) {
            project.pluginManager.apply(JacocoPlugin::class.java)

            with(project.extensions.getByType(JacocoPluginExtension::class.java)) {
                toolVersion = "0.8.4"
                reportsDir = File("${project.buildDir}/reports/jacoco")
            }

            project.afterEvaluate {
                it.getTasksByName("jacocoTestReport", false).forEach { task ->
                    val taskReport = task as JacocoReport
                    taskReport.reports.xml.isEnabled = true
                    taskReport.reports.xml.destination = File("${project.buildDir}/reports/jacoco/test.xml")
                }
            }
        }
    }

    private fun applySonar(project: Project) {
        if (project === project.rootProject) {
            project.pluginManager.apply(SonarQubePlugin::class.java)

            with(project.extensions.getByType(SonarQubeExtension::class.java)) {
                properties {
                    it.property("sonar.projectName", project.name)
                    it.property("sonar.projectKey", project.name)
                    it.property("sonar.coverage.jacoco.xmlReportPath", "${project.buildDir}/reports/jacoco/test.xml")
                    it.property("sonar.host.url", System.getProperty("sonarUrl") ?: "https://build-inera.nordicmedtest.se/sonar")
                    it.property("sonar.test.exclusions", "src/test/**")
                    it.property("sonar.exclusions",
                        listOf("**/stub/**", "**/test/**", "**/exception/**", "**/*Exception*.java", "**/*Fake*.java", "**/vendor/**",
                            "**/*testability/**", "**/swagger-ui/**", "**/generatedSource/**", "**/templates.js"))
                    it.property("sonar.javascript.lcov.reportPath", "build/karma/merged_lcov.info")
                }
            }
        }
    }

    private fun applyOwasp(project: Project) {
        if (project === project.rootProject) {
            project.pluginManager.apply(DependencyCheckPlugin::class.java)
        }
    }

    private fun applySharedTestReport(project: Project) {
        val reportTask = project.tasks.create("testReport", SharedTestReportTask::class.java)
        project.subprojects.forEach { subProject ->
            subProject.afterEvaluate {
                // We want this task to finalize all test tasks, so that it is run whether any tests failed or not.
                // B/c of a limitation in gradle, we cannot both depend on a task AND finalize it. Therefore we depend
                // on the output of the test tasks, rather than the test tasks themselves.
                reportTask.reportOn(project.getTasksByName("test", true).map { task -> (task as Test).binResultsDir })
                it.tasks.withType(Test::class.java).forEach { task -> task.finalizedBy(reportTask) }
            }
        }
    }

    private fun applyVersionPropertyFile(project: Project) {
        project.afterEvaluate { p ->
            p.tasks.withType(VersionPropertyFileTask::class.java)
                .forEach { it.dependsOn(p.getTasksByName("processResources", false)) }
            p.tasks.withType(Jar::class.java)
                .forEach { it.dependsOn(p.tasks.withType(VersionPropertyFileTask::class.java)) }
        }
    }

    private fun copyFile(sourceFile: java.io.File, destinationDir: Path) {
        if (sourceFile.isFile && destinationDir.toFile().isDirectory) {
            sourceFile.inputStream().use { input ->
                Files.copy(input, destinationDir.resolve(sourceFile.name), StandardCopyOption.REPLACE_EXISTING)
            }
            val supportedAttr = destinationDir.getFileSystem().supportedFileAttributeViews();
            if (supportedAttr.contains("posix")) {
                // Underliggande system stödjer POSIX
                // Assign permissions (chmod 755).
                val perms = PosixFilePermissions.fromString("rwxr-xr-x")
                Files.setPosixFilePermissions(destinationDir.resolve(sourceFile.name), perms)
            } else {
                val file = destinationDir.resolve(sourceFile.name).toFile()
                file.setReadable(true, false)       // Everyone can read
                file.setWritable(true, true)        // Only the owner can write
                file.setExecutable(true, false)     // Everyone can execute
            }
        }
    }

    private fun isRootProject(project: Project) : Boolean {
        return project.rootDir.path.equals(project.projectDir.path)
    }

    // Add the short name of a task type to the project properties. This makes it possible to refer to the task type without its
    // fully qualified name and without an import in a (groovy) gradle script using this plugin.
    // I.e. "task createVersionPropertyFile(type: VersionPropertyFileTask)" instead of
    //      "task createVersionPropertyFile(type: se.inera.intyg.VersionPropertyFileTask)"
    private fun addGlobalTaskType(project: Project, type: Class<*>) {
        project.extensions.extraProperties.set(type.simpleName, type)
    }

    private fun getPluginJarPath(project: Project) =
        project.rootProject.buildscript.configurations.getByName("classpath")
            .filter { it.name.contains(PLUGIN_NAME) }.asPath

    private fun useSpotbugs(project: Project) =
        !(project.rootProject.hasProperty(SPOTBUGS_EXCLUDE) &&
            project.name.matches((project.rootProject.property(SPOTBUGS_EXCLUDE) as String).toRegex()))

    private fun useErrorprone(project: Project) =
        !(project.rootProject.hasProperty(ERRORPRONE_EXCLUDE) &&
            project.name.matches((project.rootProject.property(ERRORPRONE_EXCLUDE) as String).toRegex()))

}

fun addProjectPropertiesFromFile(project: Project, propfile: java.io.File) {
    val props = Properties()
    props.load(FileInputStream(propfile))
    project.allprojects.forEach { subProject ->
        props.entries.forEach { (key, value) ->
            subProject.extensions.extraProperties.set(key as String, value as String)
        }
    }
}

fun findResolvedVersion(project: Project, groupName: String): String {
    val compileConfiguration = project.configurations.findByName("compile") ?: throw RuntimeException("No compile configuration!")
    compileConfiguration.resolvedConfiguration.resolvedArtifacts.forEach {
        if (it.moduleVersion.id.group == groupName) {
            return it.moduleVersion.id.version
        }
    }
    throw RuntimeException("No group with name $groupName found in project ${project.name}")
}

fun findGitRepository(rootDirectory: java.io.File): Repository {
    val repository = FileRepositoryBuilder()
        .findGitDir(rootDirectory)!!
        .apply { gitDir ?: throw Exception("Project must be in a git directory for git-hooks to work. Recommended solution: git init") }
        .build()
    return repository
}
