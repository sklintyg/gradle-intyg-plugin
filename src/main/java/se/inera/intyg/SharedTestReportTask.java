package se.inera.intyg;

import org.gradle.api.tasks.testing.TestReport;

import java.util.concurrent.Callable;

public class SharedTestReportTask extends TestReport {

	public SharedTestReportTask() {
		setDescription("Create a consolidated test report for JUnit tests in all sub projects.");
		setDestinationDir(getProject().file(getProject().getBuildDir() + "/reports/allTests"));
		reportOn((Callable) () -> getProject().getTasksByName("test", true));
		finalizedBy((Callable) () -> getProject().getTasksByName("test", true));
	}

}
