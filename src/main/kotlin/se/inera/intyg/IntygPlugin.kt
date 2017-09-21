package se.inera.intyg

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar

val PLUGIN_NAME = "se.inera.intyg.plugin.common"

class IntygPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.logger.quiet("Applying " + PLUGIN_NAME + " to project " + project.name + ".")

        project.pluginManager.apply(JavaPlugin::class.java)

        PluginMethods.applyCheckstyle(project)
        PluginMethods.applyFindbugs(project)
        PluginMethods.applyErrorprone(project)
        PluginMethods.applyLicence(project)
        PluginMethods.applyJacoco(project)
        PluginMethods.applySonar(project)
        PluginMethods.applySharedTestReport(project)

        PluginMethods.addGlobalTaskType(project, TagReleaseTask::class.java)
        PluginMethods.addGlobalTaskType(project, VersionPropertyFileTask::class.java)

        project.tasks.withType(Jar::class.java).forEach { task -> task.dependsOn(project.tasks.withType(VersionPropertyFileTask::class.java)) }
    }

}
