package se.inera.intyg;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;

public class ProjectUtil {

    public static String findResolvedVersion(Project project, String groupName) {
        Optional<Configuration> compileConfiguration = project.getConfigurations().stream()
                .filter(configuration -> configuration.getName().equals("compile")).findFirst();
        for (ResolvedArtifact dependency : compileConfiguration.get().getResolvedConfiguration().getResolvedArtifacts()) {
            if (dependency.getModuleVersion().getId().getGroup().equals(groupName)) {
                return dependency.getModuleVersion().getId().getVersion();
            }
        }
        throw new RuntimeException("No group with name " + groupName + " found in project " + project.getName());
    }

    public static void addProjectPropertiesFromFile(Project project, File propfile) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(propfile));
            project.getAllprojects().forEach(subProject -> {
                for (Map.Entry<Object, Object> e : props.entrySet()) {
                    subProject.getExtensions().getExtraProperties().set((String) e.getKey(), e.getValue());
                    System.out.println(e);
                }
            });
        } catch (IOException e) {
            throw new GradleScriptException("File '" + propfile.getPath() + "' does not exist.", null);
        }
    }

}
