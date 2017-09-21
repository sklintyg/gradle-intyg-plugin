package se.inera.intyg;

import java.util.Arrays;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.sonarqube.gradle.SonarQubeExtension;
import org.sonarqube.gradle.SonarQubePlugin;

public class PluginMethods {

    private static final String CODE_QUALITY_FLAG = "codeQuality";

    static void applyJacoco(Project project) {
        if (project.hasProperty(CODE_QUALITY_FLAG)) {
            project.getPluginManager().apply(JacocoPlugin.class);

            JacocoPluginExtension extension = project.getExtensions().getByType(JacocoPluginExtension.class);
            extension.setToolVersion("0.7.6.201602180812");

            project.afterEvaluate(aProject -> {
                project.getTasks().withType(Test.class).forEach(task -> {
                    task.getExtensions().findByType(JacocoTaskExtension.class)
                            .setDestinationFile(project.file(project.getBuildDir() + "/jacoco/test.exec"));
                });
            });
        }
    }

    static void applySonar(Project project) {
        if (project == project.getRootProject()) {
            project.getPluginManager().apply(SonarQubePlugin.class);

            SonarQubeExtension extension = project.getExtensions().getByType(SonarQubeExtension.class);
            extension.properties(properties -> {
                properties.property("sonar.projectName", project.getName());
                properties.property("sonar.projectKey", project.getName());
                properties.property("sonar.jacoco.reportPath", "build/jacoco/test.exec");
                properties.property("sonar.host.url",
                        System.getProperty("sonarUrl") != null ? System.getProperty("sonarUrl")
                                : "https://build-inera.nordicmedtest.se/sonar");
                properties.property("sonar.test.exclusions", "src/test/**");
                properties.property("sonar.exclusions", Arrays.asList("**/stub/**", "**/test/**", "**/exception/**", "**/*Exception*.java",
                        "**/*Fake*.java", "**/vendor/**", "**/*testability/**", "**/swagger-ui/**", "**/generatedSource/**",
                        "**/templates.js"));
                properties.property("sonar.javascript.lcov.reportPath", "build/karma/merged_lcov.info");
            });
        }
    }

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
