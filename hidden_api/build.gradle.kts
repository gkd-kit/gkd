plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "li.songe.gkd"
    compileSdk = project.properties["android_compileSdk"].toString().toInt()
    buildToolsVersion = project.properties["android_buildToolsVersion"].toString()

    defaultConfig {
        minSdk = project.properties["android_minSdk"].toString().toInt()
    }

    buildTypes {
        all {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    annotationProcessor(libs.rikka.processor)
    compileOnly(libs.rikka.annotation)
}