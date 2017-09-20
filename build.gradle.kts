import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

plugins {
    `maven-publish`
    `java-gradle-plugin`
    id("org.ajoberstar.grgit") version "2.0.1"
}

group = "se.inera.intyg.plugin.common"
version = System.getProperty("buildVersion") ?: "1.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    (plugins) {
        "intygPlugin" {
            id = "se.inera.intyg.plugin.common"
            implementationClass = "se.inera.intyg.IntygPlugin"
        }
    }
}

task("tagRelease") {
    description = "Tags the current head with the projects version."
    doLast {
        val git = Git(FileRepositoryBuilder().setGitDir(File(project.rootProject.projectDir, ".git")).readEnvironment().findGitDir().build())
        git.tag().setName("v" + project.version).setMessage("Release of " + project.version).call()
        git.push().setCredentialsProvider(UsernamePasswordCredentialsProvider(System.getProperty("githubUser"), System.getProperty("githubPassword")))
                .setPushTags().call()
    }
}

publishing {
    repositories {
        maven {
            url = uri("https://build-inera.nordicmedtest.se/nexus/repository/releases/")
            credentials {
                username = System.getProperty("nexusUsername")
                password = System.getProperty("nexusPassword")
            }
        }
    }
}

dependencies {
    compile("org.codehaus.groovy:groovy-all:2.4.11")
    compile("org.ajoberstar:grgit:1.9.0")
    compile("net.ltgt.gradle:gradle-errorprone-plugin:0.0.10")
    compile("gradle.plugin.nl.javadude.gradle.plugins:license-gradle-plugin:0.13.1")
    compile("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:2.5")

    testCompile("org.spockframework:spock-core:1.0-groovy-2.4")
}

repositories {
    maven { url = uri("https://plugins.gradle.org/m2/") }
    mavenCentral()
}
