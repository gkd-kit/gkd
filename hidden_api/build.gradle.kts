plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "li.songe.gkd"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildToolsVersion.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        aidl = true
        buildConfig = false
    }
}

dependencies {
    annotationProcessor(libs.rikka.processor)
    compileOnly(libs.rikka.annotation)
}