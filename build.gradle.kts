
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

plugins {
    maven
    `maven-publish`
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.31"

    id("org.ajoberstar.grgit") version "3.1.1"
}

group = "se.inera.intyg.plugin.common"
version = System.getProperty("buildVersion") ?: "2.1.0-SNAPSHOT"

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions { jvmTarget = "1.8" }
}

gradlePlugin {
    plugins {
        create("intygPlugin") {
            id = "se.inera.intyg.plugin.common"
            implementationClass = "se.inera.intyg.IntygPlugin"
        }
    }
}

task("tagRelease") {
    description = "Tags the current head with the projects version."
    doLast {
        val git = Git(FileRepositoryBuilder().setGitDir(File(project.rootProject.projectDir, ".git")).readEnvironment().findGitDir().build())
        git.tag().setName("v${project.version}").setMessage("Release of ${project.version}").call()
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
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.ajoberstar.grgit:grgit-core:3.1.1")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:0.0.16")
    implementation("gradle.plugin.nl.javadude.gradle.plugins:license-gradle-plugin:0.13.1")
    implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:2.8")
    implementation("gradle.plugin.com.github.spotbugs:spotbugs-gradle-plugin:2.0.0")
    implementation("org.owasp:dependency-check-gradle:5.2.2")
}

repositories {
    maven("https://plugins.gradle.org/m2/")
    mavenLocal()
    maven("https://build-inera.nordicmedtest.se/nexus/repository/public/")
    jcenter()
}
