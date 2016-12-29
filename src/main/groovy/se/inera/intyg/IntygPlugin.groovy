package se.inera.intyg

import org.gradle.api.Plugin
import org.gradle.api.Project

class IntygPlugin implements Plugin<Project> {

    @Override
    def void apply(Project project) {
        project.apply(plugin: 'java')

        addGlobalTaskType(project, TagReleaseTask.class)
        
        addGlobalTaskType(project, SharedTestReportTask.class)
        
        addGlobalTaskType(project, VersionPropertyFileTask)
        project.tasks.jar.dependsOn(project.tasks.withType(VersionPropertyFileTask.class))

        project.ext.findResolvedVersion = this.&findResolvedVersion
    }

    private static void addGlobalTaskType(Project project, Class type) {
        project.extensions.extraProperties.set(type.getSimpleName(), type)
    }

    public static String findResolvedVersion(Project project, String groupName) {
        String version
        project.configurations.find { it.name == 'compile' }.
        resolvedConfiguration.getFirstLevelModuleDependencies().find {
            if (it.moduleGroup == groupName) {
                version = it.moduleVersion
                return true
            }
            return false
        }
        return version
    }

}
