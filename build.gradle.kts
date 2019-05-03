import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

plugins {
  maven
  `maven-publish`
  `java-gradle-plugin`
  kotlin("jvm") version "1.3.31"

  id("org.ajoberstar.grgit") version "3.0.0"
}

group = "se.inera.intyg.plugin.common"
version = System.getProperty("buildVersion") ?: "3.1.0-SNAPSHOT"

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "1.8"
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
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.0.0-RC14")
  implementation("org.ajoberstar.grgit:grgit-core:3.0.0")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:0.8")
  implementation("com.google.errorprone:error_prone_core:2.3.3")
  implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:2.7")
  implementation("gradle.plugin.com.github.spotbugs:spotbugs-gradle-plugin:1.7.1")
  implementation("gradle.plugin.com.hierynomus.gradle.plugins:license-gradle-plugin:0.15.0")
  implementation("org.springframework:spring-core:5.1.6.RELEASE") {
    isForce = true
  }
}

repositories {
  maven("https://plugins.gradle.org/m2/")
  mavenLocal()
  mavenCentral()
}
