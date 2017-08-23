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
import org.gradle.api.artifacts.ResolvedDependency;

public class ProjectUtil {

    public static String findResolvedVersion(Project project, String groupName) {
        Optional<Configuration> compileConfiguration = project.getConfigurations().stream()
                .filter(configuration -> configuration.getName().equals("compile")).findFirst();
        for (ResolvedDependency dependency : compileConfiguration.get().getResolvedConfiguration().getFirstLevelModuleDependencies()) {
            if (dependency.getModuleGroup().equals(groupName)) {
                return dependency.getModuleVersion();
            }
        }
        throw new RuntimeException("No configuration with name " + groupName + " found");
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
