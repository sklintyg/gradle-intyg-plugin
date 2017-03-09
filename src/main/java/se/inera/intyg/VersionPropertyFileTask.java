package se.inera.intyg;

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class VersionPropertyFileTask extends DefaultTask {
	private String projectVersion = getProject().getVersion().toString();
	private String propertyFile = getProject().getBuildDir().getPath() + "/resources/main/version.properties";
	private String buildNumber = System.getenv("BUILD_NUMBER") != null ? System.getenv("BUILD_NUMBER") : "";
    private Date buildTime = new Date();
	private String gitCommit;
	private String gitBranch;

	public VersionPropertyFileTask() throws Exception {
		dependsOn((Callable) () -> getProject().getTasksByName("processResources", false));

		setDescription("Create a version property file from current build properties.");

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try (Repository repo = builder.setGitDir(new File(getProject().getRootProject().getProjectDir(), ".git"))
				.readEnvironment().findGitDir().build()) {
			gitCommit = repo.findRef(HEAD).getName();
			gitBranch = repo.getBranch();
		}

		getInputs().property("project.version", projectVersion);
		getInputs().property("gitCommit", gitCommit);
		getInputs().property("gitBranch", gitBranch);
		getInputs().property("buildNumber", buildNumber);
		getOutputs().file(propertyFile);
	}

	@TaskAction
	public void createPropertyFile() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("project.version", projectVersion);
		properties.setProperty("gitCommit", gitCommit);
		properties.setProperty("gitBranch", gitBranch);
		properties.setProperty("buildNumber", buildNumber);
		properties.setProperty("buildTime", DateFormat.getDateTimeInstance().format(buildTime));

		Files.createDirectories(Paths.get(propertyFile).getParent());
		try (FileOutputStream fos = new FileOutputStream(propertyFile)) {
			properties.store(fos, "");
		}
	}

}
