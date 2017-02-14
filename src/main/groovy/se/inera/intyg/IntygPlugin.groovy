package se.inera.intyg

import org.gradle.api.Plugin
import org.gradle.api.Project

class IntygPlugin implements Plugin<Project> {

    private static final PLUGIN_NAME = "gradle-intyg"

    @Override
    def void apply(Project project) {
        project.apply(plugin: 'java')

        applyCheckstyle(project)
        applyErrorprone(project)
        applyLicence(project)

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

    private void applyErrorprone(Project project) {
        project.apply(plugin: 'net.ltgt.errorprone-base')

        // This makes sure only production code (not tests) is checked
        if (project.hasProperty('codeQuality')) {
            project.compileJava {
                toolChain net.ltgt.gradle.errorprone.ErrorProneToolChain.create(project)
                options.compilerArgs += ['-Xep:BoxedPrimitiveConstructor:ERROR', '-Xep:ClassCanBeStatic:ERROR', '-Xep:DefaultCharset:ERROR',
                                         '-Xep:Finally:ERROR', '-Xep:FunctionalInterfaceClash:ERROR', '-Xep:ImmutableEnumChecker:ERROR',
                                         '-Xep:MissingOverride:ERROR', '-Xep:NarrowingCompoundAssignment:ERROR', '-Xep:NonOverridingEquals:ERROR',
                                         '-Xep:TypeParameterUnusedInFormals:ERROR', '-Xep:TypeParameterUnusedInFormals:ERROR']
            }
        }
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
            include("**/*.java")
            mapping("java", "SLASHSTAR_STYLE")
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
