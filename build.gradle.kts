// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven {
            url = uri("https://jitpack.io")
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.1")

//        当前 android 项目 kotlin 的版本
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.8.10")

        classpath("dev.rikka.tools.refine:gradle-plugin:3.0.3")
    }
}



tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}