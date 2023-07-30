import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

plugins {
  `maven-publish`
  `java-gradle-plugin`
  kotlin("jvm") version "1.9.0"

  id("org.ajoberstar.grgit") version "4.1.1"
}

group = "se.inera.intyg.plugin.common"
version = System.getProperty("buildVersion") ?: "3.2.9-SNAPSHOT"

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
        .setPushAll().setPushTags().call()
  }
}

task("install") {
  description = "Publishes artifact to local maven repository."
  dependsOn("publishPluginMavenPublicationToMavenLocal")
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
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.0")
  implementation("org.ajoberstar.grgit:grgit-core:4.1.1")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:3.0.1")
  implementation("com.google.errorprone:error_prone_core:2.20.0")
  implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.3")
  implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:5.0.14")
  implementation("gradle.plugin.com.hierynomus.gradle.plugins:license-gradle-plugin:0.16.1")
  implementation("org.springframework:spring-core:5.3.28")
  implementation("org.owasp:dependency-check-gradle:8.3.1")
}

repositories {
  gradlePluginPortal()
  mavenLocal()
  maven("https://nexus.drift.inera.se/repository/it-public/")
  mavenCentral()
}
