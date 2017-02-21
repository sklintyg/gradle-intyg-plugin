package se.inera.intyg

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.FindBugs

class IntygPlugin implements Plugin<Project> {

    private static final PLUGIN_NAME = "gradle-intyg"
    private static final ERRORPRONE_EXCLUDE = "errorproneExclude"

    @Override
    def void apply(Project project) {
        project.apply(plugin: 'java')

        applyCheckstyle(project)
        applyFindbugs(project)
        applyErrorprone(project)
        applyLicence(project)
        applyJacoco(project)
        applySonar(project)

        addGlobalTaskType(project, TagReleaseTask.class)

        addGlobalTaskType(project, SharedTestReportTask.class)

        addGlobalTaskType(project, VersionPropertyFileTask.class)
        project.tasks.jar.dependsOn(project.tasks.withType(VersionPropertyFileTask.class))

        project.ext.findResolvedVersion = this.&findResolvedVersion
    }

    private void applyCheckstyle(Project project) {
        project.apply(plugin: 'checkstyle')

        project.checkstyle {
            def intygJar = project.rootProject.buildscript.configurations.classpath.find { it.name.contains(PLUGIN_NAME) }
            config = project.resources.text.fromArchiveEntry(intygJar.path, "/checkstyle/checkstyle.xml")
            ignoreFailures = false
            showViolations = true
        }

        project.checkstyleMain.onlyIf { project.hasProperty('codeQuality') }
        project.checkstyleMain.source = "src/main/java" // Explicitly disable generated code
        project.checkstyleTest.enabled = false
    }

    private void applyFindbugs(Project project) {
        // Ugly fix to exclude 'specifications' projects. Remove when Fitnesse is finally gone.
        if (project.hasProperty('codeQuality') && !(project.name.endsWith('specifications'))) {
            project.apply(plugin: 'findbugs')

            project.findbugs {
                def intygJar = project.rootProject.buildscript.configurations.classpath.find { it.name.contains(PLUGIN_NAME) }
                includeFilterConfig = project.resources.text.fromArchiveEntry(intygJar.path, "/findbugs/findbugsIncludeFilter.xml")
                ignoreFailures = false
                effort = "max"
                reportLevel = "low"
                sourceSets = [sourceSets.main]
            }

            project.tasks.withType(FindBugs.class) {
                classes = classes.filter {
                    // There are sometimes xml- or sql-files in the build directory and these can obviously not be examined as java classes
                    !it.path.endsWith(".xml") && !it.path.endsWith(".sql")
                }
                reports {
                    if (project.properties['findBugsHtmlOutput'] == "true") {
                        xml.enabled = false
                        html.enabled = true
                    } else {
                        xml.enabled = true
                        html.enabled = false
                    }
                }
            }
        }
    }

    private void applyErrorprone(Project project) {
        if (project.hasProperty('codeQuality') && useErrorprone(project)) {
            project.apply(plugin: 'net.ltgt.errorprone-base')

            project.compileJava {
                toolChain net.ltgt.gradle.errorprone.ErrorProneToolChain.create(project)
                options.compilerArgs += ['-Xep:BoxedPrimitiveConstructor:ERROR', '-Xep:ClassCanBeStatic:ERROR',
                                         '-Xep:DefaultCharset:ERROR', '-Xep:Finally:ERROR', '-Xep:FunctionalInterfaceClash:ERROR',
                                         '-Xep:ImmutableEnumChecker:ERROR', '-Xep:MissingCasesInEnumSwitch:ERROR',
                                         '-Xep:MissingOverride:ERROR', '-Xep:NarrowingCompoundAssignment:ERROR',
                                         '-Xep:NonOverridingEquals:ERROR', '-Xep:TypeParameterUnusedInFormals:ERROR',
                                         '-Xep:TypeParameterUnusedInFormals:ERROR', '-Xep:UnnecessaryDefaultInEnumSwitch:ERROR']
            }
        }
    }

    private boolean useErrorprone(Project project) {
        return !(project.rootProject.hasProperty(ERRORPRONE_EXCLUDE)
                && project.name.matches(project.rootProject.property(ERRORPRONE_EXCLUDE)))
    }

    private void applyLicence(Project project) {
        project.apply(plugin: 'com.github.hierynomus.license')

        project.license {
            ext.project_name = "sklintyg"
            ext.project_url = "https://github.com/sklintyg"
            ext.year = Calendar.getInstance().get(Calendar.YEAR)
            strictCheck = true
            header = null
            headerURI = IntygPlugin.class.getResource("/license/header.txt").toURI()
            include("src/**/*.java")
            mapping("java", "SLASHSTAR_STYLE")
        }
    }

    private void applyJacoco(Project project) {
        if (project.hasProperty('codeQuality')) {
            project.apply(plugin: 'jacoco')

            project.jacoco {
                toolVersion = "0.7.6.201602180812"
            }

            project.test {
                jacoco {
                    destinationFile = project.file("${project.buildDir}/jacoco/test.exec")
                }
            }
        }
    }

    private void applySonar(Project project) {
        if (project == project.rootProject) {
            project.apply(plugin: "org.sonarqube")

            project.sonarqube {
                properties {
                    property "sonar.jacoco.reportPath", "${project.buildDir}/jacoco/test.exec"
                    property "sonar.host.url", System.properties['sonarUrl'] ?: "https://build-inera.nordicmedtest.se/sonar"
                    property "sonar.test.exclusions", "src/test/**"
                    property "sonar.exclusions", ["**/stub/**", "**/test/**", "**/exception/**", "**/*Exception*.java", "**/*Fake*.java",
                                                  "**/vendor/**", "**/*testability/**", "**/swagger-ui/**", "**/generatedSource/**",
                                                  "**/templates.js"]
                    property "sonar.javascript.lcov.reportPath", "${project.buildDir}/karma/merged_lcov.info"
                }
            }
        }
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
