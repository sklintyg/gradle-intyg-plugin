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
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
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

        addGlobalTaskType(project, TagReleaseTask::class.java)
        addGlobalTaskType(project, VersionPropertyFileTask::class.java)
        addGlobalTaskType(project, ArchiveDirectoryTask::class.java)
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
            getTasksByName("checkstyleMain", false).forEach {
                val csTask = it as Checkstyle
                csTask.setSource("src/main/java") // Explicitly disable generated code
                csTask.onlyIf { project.hasProperty(CODE_QUALITY_FLAG) }
            }
            getTasksByName("checkstyleTest", false).forEach { task -> task.enabled = false }
        }
    }

    private fun applySpotbugs(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useSpotbugs(project)) {
            project.pluginManager.apply(SpotBugsPlugin::class.java)

            with(project.extensions.getByType(SpotBugsExtension::class.java)) {
                includeFilterConfig = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "/spotbugs/spotbugsIncludeFilter.xml")
                isIgnoreFailures = false
                effort = "max"
                reportLevel = "low"
                sourceSets = setOf(project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main"))
            }

            project.afterEvaluate {
                tasks.withType(SpotBugsTask::class.java).forEach { task ->
                    task.classes = task.classes.filter { clazz ->
                        // There are sometimes text files in the build directory and these can obviously not be examined as java classes.
                        !clazz.path.matches(".*\\.(xml|sql|json|log|txt|ANS|properties)\\$".toRegex())
                    }
                    task.reports {
                        xml.isEnabled = false
                        html.isEnabled = true
                    }
                }
            }

        }
    }

    private fun applyErrorprone(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useErrorprone(project)) {
            project.pluginManager.apply(ErrorProneBasePlugin::class.java)

            project.afterEvaluate {
                getTasksByName("compileJava", false).forEach {
                    val jTask = it as JavaCompile
                    jTask.toolChain = net.ltgt.gradle.errorprone.ErrorProneToolChain.create(project)
                    jTask.options.compilerArgs.addAll(listOf(
                            "-Xep:BoxedPrimitiveConstructor:ERROR", "-Xep:ClassCanBeStatic:ERROR",
                            "-Xep:DefaultCharset:ERROR", "-Xep:Finally:ERROR", "-Xep:FunctionalInterfaceClash:ERROR",
                            "-Xep:ImmutableEnumChecker:ERROR", "-Xep:MissingCasesInEnumSwitch:ERROR",
                            "-Xep:MissingOverride:ERROR", "-Xep:NarrowingCompoundAssignment:ERROR",
                            "-Xep:NonOverridingEquals:ERROR", "-Xep:TypeParameterUnusedInFormals:ERROR",
                            "-Xep:TypeParameterUnusedInFormals:ERROR", "-Xep:UnnecessaryDefaultInEnumSwitch:WARN"))
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
                tasks.withType(License::class.java).forEach { task ->
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
                toolVersion = "0.7.6.201602180812"
            }

            project.afterEvaluate {
                tasks.withType(Test::class.java).forEach { task ->
                    val taskExtension = task.extensions.findByType(JacocoTaskExtension::class.java) ?: throw RuntimeException("No jacoco extension")
                    taskExtension.destinationFile = file("$buildDir/jacoco/test.exec")
                }
            }
        }
    }

    private fun applySonar(project: Project) {
        if (project === project.rootProject) {
            project.pluginManager.apply(SonarQubePlugin::class.java)

            with(project.extensions.getByType(SonarQubeExtension::class.java)) {
                properties {
                    property("sonar.projectName", project.name)
                    property("sonar.projectKey", project.name)
                    property("sonar.jacoco.reportPath", "build/jacoco/test.exec")
                    property("sonar.host.url", System.getProperty("sonarUrl") ?: "https://build-inera.nordicmedtest.se/sonar")
                    property("sonar.test.exclusions", "src/test/**")
                    property("sonar.exclusions",
                            listOf("**/stub/**", "**/test/**", "**/exception/**", "**/*Exception*.java", "**/*Fake*.java", "**/vendor/**",
                                    "**/*testability/**", "**/swagger-ui/**", "**/generatedSource/**", "**/templates.js"))
                    property("sonar.javascript.lcov.reportPath", "build/karma/merged_lcov.info")
                }
            }
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
                tasks.withType(Test::class.java).forEach { task -> task.finalizedBy(reportTask) }
            }
        }
    }

    private fun applyVersionPropertyFile(project: Project) {
        project.afterEvaluate {
            tasks.withType(VersionPropertyFileTask::class.java).forEach { it.dependsOn(getTasksByName("processResources", false)) }
            tasks.withType(Jar::class.java).forEach { it.dependsOn(tasks.withType(VersionPropertyFileTask::class.java)) }
        }
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

    private fun copyFile(sourceFile: java.io.File, destinationDir: Path) {
        if (sourceFile.isFile && destinationDir.toFile().isDirectory) {
            Files.copy(sourceFile.inputStream(), destinationDir.resolve(sourceFile.name), StandardCopyOption.REPLACE_EXISTING)

            val pfpSet = hashSetOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE)

            // Assign permissions (chmod 755).
            Files.setPosixFilePermissions(destinationDir.resolve(sourceFile.name), pfpSet)
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
            .build()!!
    return repository;
}

