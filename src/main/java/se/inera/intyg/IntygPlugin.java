package se.inera.intyg;

import javax.annotation.Nonnull;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.jvm.tasks.Jar;

class IntygPlugin implements Plugin<Project> {

    static final String PLUGIN_NAME = "se.inera.intyg.plugin.common";

    @Override
    public void apply(@Nonnull Project project) {
        project.getLogger().quiet("Applying " + PLUGIN_NAME + " to project " + project.getName() + ".");

        project.getPluginManager().apply(JavaPlugin.class);

        PluginMethods.applyCheckstyle(project);
        PluginMethods.applyFindbugs(project);
        PluginMethods.applyErrorprone(project);
        PluginMethods.applyLicence(project);
        PluginMethods.applyJacoco(project);
        PluginMethods.applySonar(project);
        PluginMethods.applySharedTestReport(project);

        PluginMethods.addGlobalTaskType(project, TagReleaseTask.class);
        PluginMethods.addGlobalTaskType(project, VersionPropertyFileTask.class);

        project.getTasks().withType(Jar.class).forEach(task -> task.dependsOn(project.getTasks().withType(VersionPropertyFileTask.class)));
    }

}
