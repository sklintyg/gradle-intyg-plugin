import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

plugins {
  maven
  `maven-publish`
  `java-gradle-plugin`
  kotlin("jvm") version "1.7.10"

  id("org.ajoberstar.grgit") version "4.1.1"
}

group = "se.inera.intyg.plugin.common"
version = System.getProperty("buildVersion") ?: "3.2.3-SNAPSHOT"

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
    }
  }
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
      url = uri("https://nexus.drift.inera.se/repository/maven-releases/")
      credentials {
        username = System.getProperty("ineraNexusUsername")
        password = System.getProperty("ineraNexusPassword")
      }
    }
  }
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.21.0")
  implementation("org.ajoberstar.grgit:grgit-core:4.1.1")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.2")
  implementation("com.google.errorprone:error_prone_core:2.15.0")
  implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.3")
  implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:4.8.0") // version 5+ when gradle 7+
  implementation("gradle.plugin.com.hierynomus.gradle.plugins:license-gradle-plugin:0.15.0")
  implementation("org.springframework:spring-core:5.3.22")
  implementation("org.owasp:dependency-check-gradle:7.2.1")
}

repositories {
  gradlePluginPortal()
  mavenLocal()
  maven("https://nexus.drift.inera.se/repository/it-public/")
  mavenCentral()
}
