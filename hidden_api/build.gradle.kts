plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = rootProject.ext["android.namespace"].toString()
    compileSdk = rootProject.ext["android.compileSdk"] as Int
    buildToolsVersion = rootProject.ext["android.buildToolsVersion"].toString()

    defaultConfig {
        minSdk = rootProject.ext["android.minSdk"] as Int
    }

    buildTypes {
        all {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = rootProject.ext["android.javaVersion"] as JavaVersion
        targetCompatibility = rootProject.ext["android.javaVersion"] as JavaVersion
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.rikka.refine.annotation)
    annotationProcessor(libs.rikka.refine.processor)
}