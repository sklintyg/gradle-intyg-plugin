package se.inera.intyg

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.jvm.tasks.Jar

val PLUGIN_NAME = "se.inera.intyg.plugin.common"
val CODE_QUALITY_FLAG = "codeQuality"

class IntygPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.quiet("Applying " + PLUGIN_NAME + " to project " + project.name + " (1).")

        project.pluginManager.apply(JavaPlugin::class.java)

        applyCheckstyle(project)
        PluginMethods.applyFindbugs(project)
        PluginMethods.applyErrorprone(project)
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

    private fun getPluginJarPath(project: Project) =
            project.rootProject.buildscript.configurations.getByName("classpath").filter { it.name.contains(PLUGIN_NAME) }.asPath

}
