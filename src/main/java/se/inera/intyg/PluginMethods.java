package se.inera.intyg;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;

public class PluginMethods {

    static void applySharedTestReport(Project project) {
        SharedTestReportTask reportTask = project.getTasks().create("testReport", SharedTestReportTask.class);
        project.getSubprojects().forEach(subproject -> {
            subproject.afterEvaluate(aProject -> {
                aProject.getTasks().withType(Test.class).forEach(task -> {
                    task.finalizedBy(reportTask);
                });
            });
        });
    }

    static void addGlobalTaskType(Project project, Class type) {
        project.getExtensions().getExtraProperties().set(type.getSimpleName(), type);
    }

}
