plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "li.songe.gkd"
    compileSdk = project.properties["android.compileSdk"].toString().toInt()
    buildToolsVersion = project.properties["android.buildToolsVersion"].toString()

    defaultConfig {
        minSdk = project.properties["android.minSdk"].toString().toInt()
    }

    buildTypes {
        all {
            isMinifyEnabled = false
        }
    }

    val androidJvmTarget = project.properties["android.jvmTarget"].toString()
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(androidJvmTarget)
        targetCompatibility = JavaVersion.toVersion(androidJvmTarget)
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.rikka.annotation)
    annotationProcessor(libs.rikka.processor)
}