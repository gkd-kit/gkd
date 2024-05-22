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
    }
}

plugins {
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.google.hilt) apply false

    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false

    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false

    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    alias(libs.plugins.rikka.refine) apply false
}

// can not work with Kotlin Multiplatform
// https://youtrack.jetbrains.com/issue/KT-33191/
//tasks.register<Delete>("clean").configure {
//    delete(rootProject.buildDir)
//}

project.gradle.taskGraph.whenReady {
    allTasks.forEach { task ->
//        error: The binary version of its metadata is 1.8.0, expected version is 1.6.0.
//        I don't know how to solve it, so just disable these tasks
        if (task.name.contains("lintAnalyzeDebug") || task.name.contains("lintVitalAnalyzeRelease")) {
            task.enabled = false
        }
    }
}

// https://kotlinlang.org/docs/js-project-setup.html#use-pre-installed-node-js
rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().download =
        false
}