package io.mrarm.irc.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import java.io.File;

public class SettingsBuilderPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        File inputYml = project.file("settings.yml");
        File outputDir = new File(project.getBuildDir(), "generated/source/settings");

        Task generateSettingsTask = project.getTasks().create("generateSettings", task -> {
            task.getInputs().file(inputYml);
            task.getOutputs().dir(outputDir);
            task.doLast(t -> {
                try {
                    SettingsBuilder.generateJavaFiles(inputYml, outputDir);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to generate settings", e);
                }
            });
        });

        // Add the generated source directory to the main source set
        project.getPlugins().withId("com.android.application", plugin -> {
            com.android.build.gradle.AppExtension android = project.getExtensions().getByType(com.android.build.gradle.AppExtension.class);
            android.getSourceSets().getByName("main").getJava().srcDir(outputDir);
        });

        // Make sure build depends on our generate task
        project.getTasks().getByName("preBuild").dependsOn(generateSettingsTask);
    }
}
