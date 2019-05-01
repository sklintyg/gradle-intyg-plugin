package se.inera.intyg

import java.io.File

open class IntygPluginCheckstyleExtension {
  var javaVersion: JavaVersion? = JavaVersion.JAVA8
  var ignoreFailures: Boolean? = false
  var showViolations: Boolean? = true
}

enum class JavaVersion(val checkstyleConfigName: String) {
  JAVA8("checkstyle_config_java8.xml"),
  JAVA11("checkstyle_config_java11.xml")
}

open class IntygPluginSpotbugsExtension {
  var ignoreFailures: Boolean? = false
  var showViolations: Boolean? = true
  var exclusionFilter: File? = null
}
