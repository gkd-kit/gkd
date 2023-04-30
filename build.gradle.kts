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

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}