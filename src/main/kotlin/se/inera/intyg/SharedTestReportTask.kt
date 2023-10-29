package se.inera.intyg

import org.gradle.api.tasks.testing.TestReport

open class SharedTestReportTask : TestReport() {
    init {
        description = "Create a consolidated test report for JUnit tests in all sub projects."
        destinationDirectory.set(project.file("${project.layout.buildDirectory.get().asFile}/reports/allTests"))
    }
}
