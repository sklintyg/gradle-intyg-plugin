#!groovy

node {
    def buildVersion = "3.2.1"

    stage('checkout') {
        git url: "https://github.com/sklintyg/gradle-intyg-plugin.git", branch: GIT_BRANCH
        util.run { checkout scm }
    }

    stage('build') {
        shgradle11 "--refresh-dependencies clean build -DbuildVersion=${buildVersion}"
    }

    stage('tag and upload') {
        shgradle11 "publishPluginMavenPublicationToMavenRepository tagRelease -DbuildVersion=${buildVersion}"
    }
}
