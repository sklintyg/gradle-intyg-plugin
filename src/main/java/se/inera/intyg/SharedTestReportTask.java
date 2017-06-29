package se.inera.intyg;

import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestReport;

import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class SharedTestReportTask extends TestReport {

    public SharedTestReportTask() {
        setDescription("Create a consolidated test report for JUnit tests in all sub projects.");
        setDestinationDir(getProject().file(getProject().getBuildDir() + "/reports/allTests"));
        // We want this task to finalize all test tasks, so that it is run whether any tests failed or not.
        // B/c of a limitation in gradle, we cannot both depend on a task AND finalize it. Therefore we depend
        // on the output of the test tasks, rather than the test tasks themselves.
        reportOn((Callable) () -> getProject().getTasksByName("test", true).stream().map(task -> ((Test) task).getBinResultsDir())
                .collect(Collectors.toList()));
    }

}
