import com.android.build.gradle.internal.cxx.json.jsonStringOf
import java.io.ByteArrayOutputStream

fun String.runCommand(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = this@runCommand.split("\\s".toRegex())
        standardOutput = byteOut
        errorOutput = ByteArrayOutputStream()
    }
    return String(byteOut.toByteArray()).trim()
}

data class GitInfo(
    val commitId: String,
    val tagName: String?,
)

val gitInfo = try {
    GitInfo(
        commitId = "git rev-parse HEAD".runCommand(),
        tagName = try {
            "git describe --tags --exact-match".runCommand()
        } catch (e: Exception) {
            println("app: current git commit is not a tag")
            null
        },
    )
} catch (e: Exception) {
    println("app: git is not available")
    null
}

val vnSuffix = "-${gitInfo?.commitId?.substring(0, 7) ?: "unknown"}"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.google.hilt)
    alias(libs.plugins.rikka.refine)
}

android {
    namespace = "li.songe.gkd"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildToolsVersion.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        applicationId = "li.songe.gkd"
        versionCode = 30
        versionName = "1.8.0-beta.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val nowTime = System.currentTimeMillis()
        buildConfigField("Long", "BUILD_TIME", jsonStringOf(nowTime) + "L")
        buildConfigField(
            "String",
            "GIT_COMMIT_ID",
            jsonStringOf(gitInfo?.commitId)
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
            if (gitInfo?.tagName == null) {
                versionNameSuffix = vnSuffix
            }
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
            versionNameSuffix = vnSuffix
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "GKD-debug")
            resValue("string", "capture_label", "捕获快照-debug")
            resValue("string", "import_desc", "GKD-debug-导入数据")
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
        aidl = true
    }
    packagingOptions.resources.excludes += setOf(
        // https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        "META-INF/**", "**/attach_hotspot_windows.dll",

        "**.properties", "**.bin", "**/*.proto",
        "**/kotlin-tooling-metadata.json",

        // ktor
        "**/custom.config.conf",
        "**/custom.config.yaml",
    )
    sourceSets.configureEach {
        kotlin.srcDir("${layout.buildDirectory.asFile.get()}/generated/ksp/$name/kotlin/")
    }
}

configurations.configureEach {
    //    https://github.com/Kotlin/kotlinx.coroutines/issues/2023
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

composeCompiler {
    // https://developer.android.com/develop/ui/compose/performance/stability/strongskipping?hl=zh-cn
    enableStrongSkippingMode = true
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}

dependencies {

    implementation(project(mapOf("path" to ":selector")))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
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

    implementation(libs.tencent.mmkv)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

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

    implementation(libs.reorderable)

    implementation(libs.androidx.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    implementation(libs.exp4j)

    implementation(libs.toaster)
    implementation(libs.permissions)
}