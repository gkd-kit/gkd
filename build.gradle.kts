// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath(libs.android.gradle)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization)
        classpath(libs.rikka.gradle)
    }
}

// https://youtrack.jetbrains.com/issue/KT-33191/
tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}

project.gradle.taskGraph.whenReady {
    allTasks.forEach { task ->
//        error: The binary version of its metadata is 1.8.0, expected version is 1.6.0.
//        I don't know how to solve it, so just disable these tasks
        if (task.name.contains("lintAnalyzeDebug") || task.name.contains("lintVitalAnalyzeRelease")) {
            task.enabled = false
        }
    }
}