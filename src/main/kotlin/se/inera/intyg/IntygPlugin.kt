package se.inera.intyg

import net.ltgt.gradle.errorprone.ErrorProneBasePlugin
import nl.javadude.gradle.plugins.license.License
import nl.javadude.gradle.plugins.license.LicenseExtension
import nl.javadude.gradle.plugins.license.LicensePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.FindBugsExtension
import org.gradle.api.plugins.quality.FindBugsPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import java.io.File
import java.io.FileInputStream
import java.util.Calendar
import java.util.Properties

val PLUGIN_NAME = "se.inera.intyg.plugin.common"
val CODE_QUALITY_FLAG = "codeQuality"
val FINDBUGS_EXCLUDE = "findbugsExclude"
val ERRORPRONE_EXCLUDE = "errorproneExclude"

class IntygPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.quiet("Applying $PLUGIN_NAME to project ${project.name}.")

        project.pluginManager.apply(JavaPlugin::class.java)

        applyCheckstyle(project)
        applyFindbugs(project)
        applyErrorprone(project)
        applyLicence(project)
        applyJacoco(project)
        applySonar(project)
        applySharedTestReport(project)

        addGlobalTaskType(project, TagReleaseTask::class.java)
        addGlobalTaskType(project, VersionPropertyFileTask::class.java)

        project.afterEvaluate {
            tasks.withType(VersionPropertyFileTask::class.java).forEach { it.dependsOn(getTasksByName("processResources", false)) }
            tasks.withType(Jar::class.java).forEach { it.dependsOn(tasks.withType(VersionPropertyFileTask::class.java)) }
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
            getTasksByName("checkstyleMain", false).forEach {
                val csTask = it as Checkstyle
                csTask.setSource("src/main/java") // Explicitly disable generated code
                csTask.onlyIf { project.hasProperty(CODE_QUALITY_FLAG) }
            }
            getTasksByName("checkstyleTest", false).forEach { task -> task.enabled = false }
        }
    }

    private fun applyFindbugs(project: Project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useFindbugs(project)) {
            project.pluginManager.apply(FindBugsPlugin::class.java)

            with(project.extensions.getByType(FindBugsExtension::class.java)) {
                includeFilterConfig = project.resources.text.fromArchiveEntry(getPluginJarPath(project), "/findbugs/findbugsIncludeFilter.xml")
                isIgnoreFailures = false
                effort = "max"
                reportLevel = "low"
                sourceSets = setOf(project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main"))
            }

            project.afterEvaluate {
                tasks.withType(FindBugs::class.java).forEach { task ->
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
                includePatterns = setOf("**/*.java")
                mapping("java", "SLASHSTAR_STYLE")
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
                tasks.withType(Test::class.java).forEach { task -> task.finalizedBy(reportTask) }
            }
        }
    }

    private fun addGlobalTaskType(project: Project, type: Class<*>) {
        project.extensions.extraProperties.set(type.simpleName, type)
    }

    private fun getPluginJarPath(project: Project) =
            project.rootProject.buildscript.configurations.getByName("classpath").filter { it.name.contains(PLUGIN_NAME) }.asPath

    private fun useFindbugs(project: Project) =
            !(project.rootProject.hasProperty(FINDBUGS_EXCLUDE) &&
                    project.name.matches((project.rootProject.property(FINDBUGS_EXCLUDE) as String).toRegex()))

    private fun useErrorprone(project: Project) =
            !(project.rootProject.hasProperty(ERRORPRONE_EXCLUDE) &&
                    project.name.matches((project.rootProject.property(ERRORPRONE_EXCLUDE) as String).toRegex()))

}

fun addProjectPropertiesFromFile(project: Project, propfile: File) {
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
