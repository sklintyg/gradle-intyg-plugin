package se.inera.intyg

import org.gradle.api.tasks.testing.TestReport

class SharedTestReportTask extends TestReport {

    public SharedTestReportTask() {
        setDescription('Create a consolidated test report for JUnit tests in all sub projects.')
        setDestinationDir(project.file("${project.buildDir}/reports/allTests"))
        reportOn(project.subprojects*.test)
    }

}
