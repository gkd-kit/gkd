plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.android")
}

@Suppress("UnstableApiUsage")
android {
    namespace = "li.songe.gkd"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.android.buildToolsVersion.get()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        applicationId = "li.songe.gkd"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        kapt {
            arguments {
//                room 依赖每次构建的产物来执行自动迁移
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
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

    kotlin {
        sourceSets.debug {
            kotlin.srcDir("build/generated/ksp/debug/kotlin")
        }
        sourceSets.release {
            kotlin.srcDir("build/generated/ksp/release/kotlin")
        }
    }

    buildTypes {
        release {
            manifestPlaceholders += mapOf()
            isMinifyEnabled = false
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appName"] = "搞快点"
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appName"] = "搞快点-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packagingOptions {
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
}

dependencies {
    implementation(project(mapOf("path" to ":selector_core")))
    implementation(project(mapOf("path" to ":router")))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.localbroadcastmanager)

    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)
    implementation(libs.compose.activity)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(libs.tencent.bugly)
    implementation(libs.tencent.mmkv)

    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)
    implementation(libs.google.accompanist.placeholder.material)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.others.jankson)
    implementation(libs.others.utilcodex)
    implementation(libs.others.activityResultLauncher)
    implementation(libs.others.zxing.android.embedded)
    implementation(libs.others.floating.bubble.view)

}