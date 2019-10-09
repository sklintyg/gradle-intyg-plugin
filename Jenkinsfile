#!groovy

node {
    def buildVersion = "3.0.9"

    stage('checkout') {
        git url: "https://github.com/sklintyg/gradle-intyg-plugin.git", branch: GIT_BRANCH
        util.run { checkout scm }
    }

    stage('build') {
        shgradle "--refresh-dependencies clean build -DbuildVersion=${buildVersion}"
    }

    stage('tag and upload') {
        shgradle "publishPluginMavenPublicationToMavenRepository tagRelease -DbuildVersion=${buildVersion}"
    }
}
