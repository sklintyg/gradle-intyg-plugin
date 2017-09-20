package se.inera.intyg;

import static java.util.Collections.singleton;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;

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
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.sonarqube.gradle.SonarQubeExtension;
import org.sonarqube.gradle.SonarQubePlugin;

import net.ltgt.gradle.errorprone.ErrorProneBasePlugin;
import nl.javadude.gradle.plugins.license.License;
import nl.javadude.gradle.plugins.license.LicenseExtension;
import nl.javadude.gradle.plugins.license.LicensePlugin;

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
        applyErrorprone(project);
        applyLicence(project);
        applyJacoco(project);
        applySonar(project);
        applySharedTestReport(project);

        addGlobalTaskType(project, TagReleaseTask.class);
        addGlobalTaskType(project, VersionPropertyFileTask.class);

        project.getTasks().withType(Jar.class).forEach(task -> task.dependsOn(project.getTasks().withType(VersionPropertyFileTask.class)));
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
            project.getTasksByName("checkstyleTest", false).forEach(task -> task.setEnabled(false));
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

    private void applyErrorprone(Project project) {
        if (project.hasProperty(CODE_QUALITY_FLAG) && useErrorprone(project)) {
            project.getPluginManager().apply(ErrorProneBasePlugin.class);

            project.afterEvaluate(aProject -> {
                project.getTasksByName("compileJava", false).forEach(task -> {
                    JavaCompile jTask = (JavaCompile) task;
                    jTask.setToolChain(net.ltgt.gradle.errorprone.ErrorProneToolChain.create(project));
                    jTask.getOptions().getCompilerArgs().addAll(Arrays.asList(
                            "-Xep:BoxedPrimitiveConstructor:ERROR", "-Xep:ClassCanBeStatic:ERROR",
                            "-Xep:DefaultCharset:ERROR", "-Xep:Finally:ERROR", "-Xep:FunctionalInterfaceClash:ERROR",
                            "-Xep:ImmutableEnumChecker:ERROR", "-Xep:MissingCasesInEnumSwitch:ERROR",
                            "-Xep:MissingOverride:ERROR", "-Xep:NarrowingCompoundAssignment:ERROR",
                            "-Xep:NonOverridingEquals:ERROR", "-Xep:TypeParameterUnusedInFormals:ERROR",
                            "-Xep:TypeParameterUnusedInFormals:ERROR", "-Xep:UnnecessaryDefaultInEnumSwitch:WARN"));
                });
            });
        }
    }

    private void applyLicence(Project project) {
        if (project.hasProperty(CODE_QUALITY_FLAG)) {
            project.getPluginManager().apply(LicensePlugin.class);

            LicenseExtension extension = project.getExtensions().getByType(LicenseExtension.class);

            extension.setStrictCheck(true);
            extension.setHeader(null);
            try {
                extension.setHeaderURI(IntygPlugin.class.getResource("/license/header.txt").toURI());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            extension.setIncludePatterns(singleton("**/*.java"));
            extension.mapping("java", "SLASHSTAR_STYLE");

            project.afterEvaluate(aProject -> {
                project.getTasks().withType(License.class).forEach(task -> {
                    task.setInheritedProperties(new HashMap<>());
                    task.getInheritedProperties().put("project_name", "sklintyg");
                    task.getInheritedProperties().put("project_url", "https://github.com/sklintyg");
                    task.getInheritedProperties().put("year", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
                });
            });
        }
    }

    private void applyJacoco(Project project) {
        if (project.hasProperty(CODE_QUALITY_FLAG)) {
            project.getPluginManager().apply(JacocoPlugin.class);

            JacocoPluginExtension extension = project.getExtensions().getByType(JacocoPluginExtension.class);
            extension.setToolVersion("0.7.6.201602180812");

            project.afterEvaluate(aProject -> {
                project.getTasks().withType(Test.class).forEach(task -> {
                    task.getExtensions().findByType(JacocoTaskExtension.class)
                            .setDestinationFile(project.file("${project.buildDir}/jacoco/test.exec"));
                });
            });
        }
    }

    private void applySonar(Project project) {
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

    private void applySharedTestReport(Project project) {
        SharedTestReportTask reportTask = project.getTasks().create("testReport", SharedTestReportTask.class);
        project.getSubprojects().forEach(subproject -> {
            subproject.afterEvaluate(aProject -> {
                aProject.getTasks().withType(Test.class).forEach(task -> {
                    task.finalizedBy(reportTask);
                });
            });
        });
    }

    private static String getPluginJarPath(Project project) {
        return project.getRootProject().getBuildscript().getConfigurations().getByName("classpath")
                .filter(dep -> dep.getName().contains(PLUGIN_NAME)).getAsPath();
    }

    private static boolean useFindbugs(Project project) {
        return !(project.getRootProject().hasProperty(FINDBUGS_EXCLUDE)
                && project.getName().matches((String) project.getRootProject().property(FINDBUGS_EXCLUDE)));
    }

    private static boolean useErrorprone(Project project) {
        return !(project.getRootProject().hasProperty(ERRORPRONE_EXCLUDE)
                && project.getName().matches((String) project.getRootProject().property(ERRORPRONE_EXCLUDE)));
    }

    private static void addGlobalTaskType(Project project, Class type) {
        project.getExtensions().getExtraProperties().set(type.getSimpleName(), type);
    }

}
