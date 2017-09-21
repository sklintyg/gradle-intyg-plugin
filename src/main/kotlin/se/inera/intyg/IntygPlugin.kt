package se.inera.intyg

import net.ltgt.gradle.errorprone.ErrorProneBasePlugin
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
import org.gradle.jvm.tasks.Jar

val PLUGIN_NAME = "se.inera.intyg.plugin.common"
val CODE_QUALITY_FLAG = "codeQuality"
val FINDBUGS_EXCLUDE = "findbugsExclude"
val ERRORPRONE_EXCLUDE = "errorproneExclude"

class IntygPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.quiet("Applying " + PLUGIN_NAME + " to project " + project.name + " (1).")

        project.pluginManager.apply(JavaPlugin::class.java)

        applyCheckstyle(project)
        applyFindbugs(project)
        applyErrorprone(project)
        PluginMethods.applyLicence(project)
        PluginMethods.applyJacoco(project)
        PluginMethods.applySonar(project)
        PluginMethods.applySharedTestReport(project)

        PluginMethods.addGlobalTaskType(project, TagReleaseTask::class.java)
        PluginMethods.addGlobalTaskType(project, VersionPropertyFileTask::class.java)

        project.tasks.withType(Jar::class.java).forEach { it.dependsOn(project.tasks.withType(VersionPropertyFileTask::class.java)) }
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

    private fun getPluginJarPath(project: Project) =
            project.rootProject.buildscript.configurations.getByName("classpath").filter { it.name.contains(PLUGIN_NAME) }.asPath

    private fun useFindbugs(project: Project) =
            !(project.rootProject.hasProperty(FINDBUGS_EXCLUDE) &&
                    project.name.matches((project.rootProject.property(FINDBUGS_EXCLUDE) as String).toRegex()))

    private fun useErrorprone(project: Project) =
            !(project.rootProject.hasProperty(ERRORPRONE_EXCLUDE) &&
                    project.name.matches((project.rootProject.property(ERRORPRONE_EXCLUDE) as String).toRegex()))

}
