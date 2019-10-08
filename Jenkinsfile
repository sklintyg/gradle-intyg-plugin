#!groovy

def buildVersion = "2.0.10"

stage('checkout') {
    node {
        git url: "https://github.com/sklintyg/gradle-intyg-plugin.git", branch: GIT_BRANCH
        util.run { checkout scm }
    }
}

stage('build') {
    node {
        shgradle "--refresh-dependencies clean build -DbuildVersion=${buildVersion}"
    }
}

stage('tag and upload') {
    node {
        shgradle "publishPluginMavenPublicationToMavenRepository tagRelease -DbuildVersion=${buildVersion}"
    }
}
