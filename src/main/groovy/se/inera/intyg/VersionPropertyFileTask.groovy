package se.inera.intyg

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.ajoberstar.grgit.Grgit

class VersionPropertyFileTask extends DefaultTask {
    def propertyFile = "${project.buildDir}/resources/main/version.properties"
    def projectVersion = project.rootProject.version
    def buildNumber = System.env.BUILD_NUMBER ?: ""
    def gitCommit
    def gitBranch
    def buildTime

    public VersionPropertyFileTask() {
        dependsOn(project.tasks.processResources)

        setDescription('Create a version property file from current build properties.')

        def grgit = Grgit.open(dir: project.rootProject.projectDir)
        gitCommit = grgit.head().id
        gitBranch = grgit.branch.getCurrent().getName()
        buildTime = new Date()

        getInputs().property("project.version", projectVersion)
        getInputs().property("gitCommit", gitCommit)
        getInputs().property("gitBranch", gitBranch)
        getInputs().property("buildNumber", buildNumber)
        getOutputs().file(propertyFile)
    }

    @TaskAction
    def createFile() {
        ant.touch(file: propertyFile, mkdirs: "true")
        ant.propertyfile(file: propertyFile) {
            entry(key: 'project.version', value: projectVersion)
            entry(key: 'gitCommit', value: gitCommit)
            entry(key: 'gitBranch', value: gitBranch)
            entry(key: 'buildNumber', value: buildNumber)
            entry(key: 'buildTime', value: buildTime)
        }
    }

}
