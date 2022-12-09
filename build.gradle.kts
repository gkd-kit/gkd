// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
//    ext {
//        compose_version = "1.0.5"
//        kotlin_version = "1.5.31"
//    }
    repositories {
        mavenLocal()
//        maven { url = uri("https://maven.aliyun.com/repository/google") }
//        maven { url = uri("https://maven.aliyun.com/repository/central") }
//        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        google()
        maven {
            url = uri("https://jitpack.io")
        }
//        maven { url = uri("https://androidx.dev/snapshots/builds/9153953/artifacts/repository") }

    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.1")
//        当前 android 项目 kotlin 的版本
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.7.20")
        classpath("dev.rikka.tools.refine:gradle-plugin:3.0.3")
    }
}



tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}