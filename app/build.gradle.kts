import com.android.build.gradle.internal.cxx.json.jsonStringOf
import java.text.SimpleDateFormat
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.google.hilt)
    alias(libs.plugins.rikka.refine)
}

android {
    namespace = "li.songe.gkd"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.android.buildToolsVersion.get()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        applicationId = "li.songe.gkd"
        versionCode = 7
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
        val nowTime = System.currentTimeMillis()
        buildConfigField("Long", "BUILD_TIME", jsonStringOf(nowTime) + "L")
        buildConfigField(
            "String", "BUILD_DATE", jsonStringOf(
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss ZZ", Locale.SIMPLIFIED_CHINESE
                ).format(nowTime)
            )
        )
    }

    lint {
        disable.add("ModifierFactoryUnreferencedReceiver")
    }

    signingConfigs {
        create("release") {
            storeFile = file("./android.jks")
            storePassword = "KdMQ6pqiNSJ6Sype"
            keyAlias = "key0"
            keyPassword = "KdMQ6pqiNSJ6Sype"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
                )
            )
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appName"] = "GKD"
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appName"] = "GKD-debug"
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compilerVersion.get()
    }
    packaging {
        resources {
            // Due to https://github.com/Kotlin/kotlinx.coroutines/issues/2023
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/licenses/*"
            excludes += "**/attach_hotspot_windows.dll"
            excludes += "META-INF/io.netty.*"
        }
    }
    configurations.all {
        resolutionStrategy {
            //    https://github.com/Kotlin/kotlinx.coroutines/issues/2023
            exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
        }
    }

//    ksp
    sourceSets.configureEach {
        kotlin.srcDir("$buildDir/generated/ksp/$name/kotlin/")
    }
}

dependencies {

    implementation(project(mapOf("path" to ":selector")))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.compose.ui)
//    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)
    implementation(libs.compose.activity)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    compileOnly(project(mapOf("path" to ":hidden_api")))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.tencent.bugly)
    implementation(libs.tencent.mmkv)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)
    implementation(libs.google.accompanist.placeholder.material)
    implementation(libs.google.accompanist.systemuicontroller)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.others.jankson)
    implementation(libs.others.utilcodex)
    implementation(libs.others.activityResultLauncher)
    implementation(libs.others.floating.bubble.view)

    implementation(libs.destinations.core)
    ksp(libs.destinations.ksp)

    implementation(libs.google.hilt.android)
    kapt(libs.google.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.others.reorderable)

    implementation(libs.androidx.splashscreen)
}