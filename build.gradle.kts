import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

plugins {
  maven
  `maven-publish`
  `java-gradle-plugin`
  kotlin("jvm") version "1.3.70"

  id("org.ajoberstar.grgit") version "3.1.1"
}

group = "se.inera.intyg.plugin.common"
version = System.getProperty("buildVersion") ?: "3.1.2-SNAPSHOT"

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
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.1.0")
  implementation("org.ajoberstar.grgit:grgit-core:3.1.1")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:1.2.1")
  implementation("com.google.errorprone:error_prone_core:2.3.4")
  implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:2.8")
  implementation("gradle.plugin.com.github.spotbugs.snom:spotbugs-gradle-plugin:4.0.2")
  implementation("gradle.plugin.com.hierynomus.gradle.plugins:license-gradle-plugin:0.15.0")
  implementation("org.springframework:spring-core:5.2.4.RELEASE") {
    isForce = true
  }
  implementation("org.owasp:dependency-check-gradle:5.2.4")

}

repositories {
  gradlePluginPortal()
  mavenLocal()
  maven("https://nexus.drift.inera.se/repository/it-public/")
  jcenter()
}
