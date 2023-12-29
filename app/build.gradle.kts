import com.android.build.gradle.internal.cxx.json.jsonStringOf
import java.text.SimpleDateFormat
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 17
        versionName = "1.6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        buildConfigField(
            "String", "GKD_BUGLY_APP_ID", jsonStringOf(project.properties["GKD_BUGLY_APP_ID"])
        )

        resourceConfigurations.addAll(listOf("zh", "en"))
        ndk {
            // noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    lint {
        disable.add("ModifierFactoryUnreferencedReceiver")
    }

    val currentSigning = if (project.hasProperty("GKD_STORE_FILE")) {
        signingConfigs.create("release") {
            storeFile = file(project.properties["GKD_STORE_FILE"] as String)
            storePassword = project.properties["GKD_STORE_PASSWORD"] as String
            keyAlias = project.properties["GKD_KEY_ALIAS"] as String
            keyPassword = project.properties["GKD_KEY_PASSWORD"] as String
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        all {
            signingConfig = currentSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(
                listOf(
                    // /sdk/tools/proguard/proguard-android-optimize.txt
                    getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
                )
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "GKD-debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.FlowPreview"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compilerVersion.get()
    }
    packagingOptions.resources.excludes += setOf(
        // https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        "META-INF/**", "**/attach_hotspot_windows.dll",

        "**.properties", "**.bin", "**/*.proto"
    )
    configurations.configureEach {
        //    https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.generateKotlin", "true")
    }
    sourceSets.configureEach {
        kotlin.srcDir("${layout.buildDirectory.asFile.get()}/generated/ksp/$name/kotlin/")
    }
}

dependencies {

    implementation(project(mapOf("path" to ":selector")))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.compose.ui)
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
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.others.jankson)
    implementation(libs.others.utilcodex)
    implementation(libs.others.activityResultLauncher)
    implementation(libs.others.floating.bubble.view)

    implementation(libs.destinations.core)
    ksp(libs.destinations.ksp)

    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.others.reorderable)

    implementation(libs.androidx.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
}