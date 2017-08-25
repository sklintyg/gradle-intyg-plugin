package se.inera.intyg;

import static java.util.Collections.singleton;

import java.util.Collections;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.plugins.quality.FindBugs;
import org.gradle.api.plugins.quality.FindBugsExtension;
import org.gradle.api.plugins.quality.FindBugsPlugin;
import org.gradle.jvm.tasks.Jar;

class IntygPlugin implements Plugin<Project> {

    private static final String PLUGIN_NAME = "se.inera.intyg.plugin.common";
    private static final String CODE_QUALITY_FLAG = "codeQuality";
    private static final String ERRORPRONE_EXCLUDE = "errorproneExclude";
    private static final String FINDBUGS_EXCLUDE = "findbugsExclude";

    @Override
    public void apply(Project project) {
        project.getLogger().quiet("Applying " + PLUGIN_NAME + " to project " + project.getName() + ".");

        project.getPluginManager().apply(JavaPlugin.class);

        applyCheckstyle(project);
        applyFindbugs(project);

        addGlobalTaskType(project, TagReleaseTask.class);
        addGlobalTaskType(project, VersionPropertyFileTask.class);
        project.getTasks().withType(Jar.class).forEach(task -> {
            task.dependsOn(project.getTasks().withType(VersionPropertyFileTask.class));
        });
    }

    private void applyCheckstyle(Project project) {
        project.getPluginManager().apply(CheckstylePlugin.class);

        CheckstyleExtension extension = project.getExtensions().getByType(CheckstyleExtension.class);
        extension.setConfig(project.getResources().getText().fromArchiveEntry(getPluginJarPath(project), "/checkstyle/checkstyle.xml"));
        extension.setConfigProperties(Collections.singletonMap("package_name", project.getRootProject().getName()));
        extension.setIgnoreFailures(false);
        extension.setShowViolations(true);

        project.afterEvaluate(aProject -> {
            project.getTasksByName("checkstyleMain", false).forEach(task -> {
                Checkstyle csTask = (Checkstyle) task;
                csTask.setSource("src/main/java"); // Explicitly disable generated code
                csTask.onlyIf(dummy -> project.hasProperty(CODE_QUALITY_FLAG));
            });
            project.getTasksByName("checkstyleTest", false).forEach(task -> {
                task.setEnabled(false);
            });
        });
    }

    private void applyFindbugs(Project project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useFindbugs(project)) {
            project.getPluginManager().apply(FindBugsPlugin.class);

            FindBugsExtension extension = project.getExtensions().getByType(FindBugsExtension.class);
            extension.setIncludeFilterConfig(project.getResources().getText().fromArchiveEntry(getPluginJarPath(project),
                    "/findbugs/findbugsIncludeFilter.xml"));
            extension.setIgnoreFailures(false);
            extension.setEffort("max");
            extension.setReportLevel("low");
            extension.setSourceSets(
                    singleton(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main")));

            project.afterEvaluate(aProject -> {
                project.getTasks().withType(FindBugs.class).forEach(task -> {
                    task.setClasses(task.getClasses().filter(clazz -> {
                        // There are sometimes text files in the build directory and these can obviously not be examined as java classes.
                        return !clazz.getPath().matches(".*\\.(xml|sql|json|log|txt|ANS|properties)\\$");
                    }));
                    task.reports(report -> {
                        report.getXml().setEnabled(false);
                        report.getHtml().setEnabled(true);
                    });
                });
            });
        }
    }

    private static String getPluginJarPath(Project project) {
        return project.getRootProject().getBuildscript().getConfigurations().getByName("classpath")
                .filter(dep -> dep.getName().contains(PLUGIN_NAME)).getAsPath();
    }

    private static boolean useFindbugs(Project project) {
        return !(project.getRootProject().hasProperty(FINDBUGS_EXCLUDE)
                && project.getName().matches((String) project.getRootProject().property(FINDBUGS_EXCLUDE)));
    }

    private static void addGlobalTaskType(Project project, Class type) {
        project.getExtensions().getExtraProperties().set(type.getSimpleName(), type);
    }

}
