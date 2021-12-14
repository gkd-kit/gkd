// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
//    ext {
//        compose_version = "1.0.5"
//        kotlin_version = "1.5.31"
//    }
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        val kotlinVersion= "1.5.31"
        classpath("com.android.tools.build:gradle:7.0.4")
        classpath(kotlin("gradle-plugin", version = kotlinVersion))

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
 }