package se.inera.intyg

open class IntygPluginCheckstyleExtension {
  var javaVersion: JavaVersion? = JavaVersion.JAVA11
  var ignoreFailures: Boolean? = false
  var showViolations: Boolean? = true
}

enum class JavaVersion(val checkstyleConfigName: String) {
  JAVA8("checkstyle_config_java8.xml"),
  JAVA11("checkstyle_config_java11.xml")
}
