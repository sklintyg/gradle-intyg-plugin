package se.inera.intyg;

import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestReport;

import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class SharedTestReportTask extends TestReport {

    public SharedTestReportTask() {
        setDescription("Create a consolidated test report for JUnit tests in all sub projects.");
        setDestinationDir(getProject().file(getProject().getBuildDir() + "/reports/allTests"));
        reportOn((Callable) () -> getProject().getTasksByName("test", true).stream().map(task -> ((Test) task).getBinResultsDir())
                .collect(Collectors.toList()));
    }

}
