package se.inera.intyg;

import java.util.Collections;
import java.util.Set;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.jvm.tasks.Jar;

class IntygPlugin implements Plugin<Project> {

    private static final String PLUGIN_NAME = "se.inera.intyg.plugin.common";
    private static final String CODE_QUALITY_FLAG = "codeQuality";
    private static final String ERRORPRONE_EXCLUDE = "errorproneExclude";
    private static final String FINDBUGS_EXCLUDE = "findbugsExclude";

    @Override
    public void apply(Project project) {
        project.getLogger().quiet("Applying IntygPlugin to project " + project.getName() + ".");

        project.getPluginManager().apply(JavaPlugin.class);

        applyCheckstyle(project);

        addGlobalTaskType(project, TagReleaseTask.class);

        addGlobalTaskType(project, VersionPropertyFileTask.class);

        project.getTasks().withType(Jar.class).forEach(task -> {
            task.dependsOn(project.getTasks().withType(VersionPropertyFileTask.class));
        });
    }

    private void applyCheckstyle(Project project) {
        project.getPluginManager().apply(CheckstylePlugin.class);

        CheckstyleExtension extension = project.getExtensions().getByType(CheckstyleExtension.class);
        FileCollection pluginJar = project.getRootProject().getBuildscript().getConfigurations().getByName("classpath")
                .filter(dep -> dep.getName().contains(PLUGIN_NAME));
        extension.setConfig(project.getResources().getText().fromArchiveEntry(pluginJar.getAsPath(), "/checkstyle/checkstyle.xml"));
        extension.setConfigProperties(Collections.singletonMap("package_name", project.getRootProject().getName()));
        extension.setIgnoreFailures(false);
        extension.setShowViolations(true);

        Set<Task> checkstyleMain = project.getTasksByName("checkstyleMain", true);
        checkstyleMain.forEach(task -> {
            Checkstyle csTask = (Checkstyle) task;
            csTask.setSource("src/main/java"); // Explicitly disable generated code
            csTask.onlyIf(dummy -> project.hasProperty(CODE_QUALITY_FLAG));
        });
        Set<Task> checkstyleTest = project.getTasksByName("checkstyleTest", true);
        checkstyleTest.forEach(task -> {
            task.setEnabled(false);
        });
    }

    private static void addGlobalTaskType(Project project, Class type) {
        project.getExtensions().getExtraProperties().set(type.getSimpleName(), type);
    }

}
