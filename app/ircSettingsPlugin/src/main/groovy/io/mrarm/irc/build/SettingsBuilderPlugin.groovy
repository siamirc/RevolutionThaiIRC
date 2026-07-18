package io.mrarm.irc.build

import org.gradle.api.Plugin
import org.gradle.api.Project

public class SettingsBuilderPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def genDir = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "generated/source/settings")
        def genTask = project.tasks.register("generateSettings") {
            doLast {
                SettingsBuilder.generateJavaFiles(project.file("settings.yml"), genDir)
            }
        }

        def android = project.extensions.getByName("android")
        android.sourceSets.main.java.srcDir(genDir)

        project.tasks.matching { it.name.startsWith("compile") && it.name.contains("Java") }.all { compileTask ->
            compileTask.dependsOn(genTask)
        }
    }

}