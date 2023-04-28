plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "li.songe.router"
    compileSdk = 33

    defaultConfig {
        minSdk = 26
        targetSdk = 33

//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
//        compose 编译器的版本, 需要注意它与 compose 的版本不一致
//        https://mvnrepository.com/artifact/androidx.compose.compiler/compiler
        kotlinCompilerExtensionVersion = "1.4.4"
    }
}

dependencies {
    implementation("com.blankj:utilcodex:1.31.0")
    implementation("androidx.compose.ui:ui:1.4.0")
    implementation("androidx.compose.material:material:1.4.0")
    implementation("androidx.activity:activity-compose:1.7.0")
}