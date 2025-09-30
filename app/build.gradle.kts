import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.reflect.full.declaredMemberProperties

fun String.runCommand(): String {
    val process = ProcessBuilder(split(" "))
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        error("Command failed with exit code $exitCode: $output")
    }
    return output
}

data class GitInfo(
    val commitId: String,
    val commitTime: String,
    val tagName: String?,
)

val gitInfo = GitInfo(
    commitId = "git rev-parse HEAD".runCommand(),
    commitTime = "git log -1 --format=%ct".runCommand() + "000",
    tagName = runCatching { "git describe --tags --exact-match".runCommand() }.getOrNull(),
)

val debugSuffixPairList by lazy {
    javax.xml.parsers.DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(file("$projectDir/src/main/res/values/strings.xml"))
        .documentElement.getElementsByTagName("string").run {
            (0 until length).mapNotNull { i ->
                val node = item(i)
                if (node.attributes.getNamedItem("debug_suffix") != null) {
                    val key = node.attributes.getNamedItem("name").nodeValue
                    val value = node.textContent
                    key to value
                } else {
                    null
                }
            }
        }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.rikka.refine)
}

android {
    namespace = rootProject.ext["android.namespace"].toString()
    compileSdk = rootProject.ext["android.compileSdk"] as Int
    buildToolsVersion = rootProject.ext["android.buildToolsVersion"].toString()

    defaultConfig {
        minSdk = rootProject.ext["android.minSdk"] as Int
        targetSdk = rootProject.ext["android.targetSdk"] as Int

        applicationId = "li.songe.gkd"
        versionCode = 67
        versionName = "1.11.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            localeFilters += listOf("zh", "en")
        }
        ndk {
            // noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        GitInfo::class.declaredMemberProperties.onEach {
            manifestPlaceholders[it.name] = it.get(gitInfo) ?: ""
        }
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    val gkdSigningConfig = signingConfigs.create("gkd") {
        storeFile = file(project.properties["GKD_STORE_FILE"] as String)
        storePassword = project.properties["GKD_STORE_PASSWORD"].toString()
        keyAlias = project.properties["GKD_KEY_ALIAS"].toString()
        keyPassword = project.properties["GKD_KEY_PASSWORD"].toString()
    }

    val playSigningConfig = if (project.hasProperty("PLAY_STORE_FILE")) {
        signingConfigs.create("play") {
            storeFile = file(project.properties["PLAY_STORE_FILE"].toString())
            storePassword = project.properties["PLAY_STORE_PASSWORD"].toString()
            keyAlias = project.properties["PLAY_KEY_ALIAS"].toString()
            keyPassword = project.properties["PLAY_KEY_PASSWORD"].toString()
        }
    } else {
        null
    }

    buildTypes {
        all {
            if (gitInfo.tagName == null) {
                versionNameSuffix = "-${gitInfo.commitId.substring(0, 7)}"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = gkdSigningConfig
            applicationIdSuffix = ".debug"
            debugSuffixPairList.onEach { (key, value) ->
                resValue("string", key, "$value-debug")
            }
        }
    }
    productFlavors {
        flavorDimensions += "channel"
        create("gkd") {
            isDefault = true
            signingConfig = gkdSigningConfig
            resValue("bool", "is_accessibility_tool", "true")
        }
        create("play") {
            signingConfig = playSigningConfig ?: gkdSigningConfig
            resValue("bool", "is_accessibility_tool", "false")
        }
        all {
            dimension = flavorDimensionList.first()
            manifestPlaceholders["channel"] = name
        }
    }
    compileOptions {
        sourceCompatibility = rootProject.ext["android.javaVersion"] as JavaVersion
        targetCompatibility = rootProject.ext["android.javaVersion"] as JavaVersion
    }
    dependenciesInfo.includeInApk = false
    packagingOptions.resources.excludes += setOf(
        // https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        "META-INF/**", "**/attach_hotspot_windows.dll",

        "**.properties", "**.bin", "**/*.proto",
        "**/kotlin-tooling-metadata.json",

        // ktor
        "**/custom.config.conf",
        "**/custom.config.yaml",
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(rootProject.ext["kotlin.jvmTarget"] as JvmTarget)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-Xcontext-parameters"
        )
    }
}

// https://developer.android.com/jetpack/androidx/releases/room?hl=zh-cn#compiler-options
room {
    schemaDirectory("$projectDir/schemas")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(
        rootProject.layout.projectDirectory.file("stability_config.conf"),
    )
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(project(":selector"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)

    implementation(libs.compose.activity)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    compileOnly(project(":hidden_api"))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.rikka.refine.runtime)
    implementation(libs.lsposed.hiddenapibypass)

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
    // https://github.com/Kotlin/kotlinx-atomicfu/issues/145
    implementation(libs.kotlinx.atomicfu)

    implementation(libs.utilcodex)
    implementation(libs.activityResultLauncher)

    implementation(libs.destinations.core)
    ksp(libs.destinations.ksp)

    implementation(libs.reorderable)

    implementation(libs.androidx.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)

    implementation(libs.exp4j)

    implementation(libs.toaster)
    implementation(libs.permissions)

    implementation(libs.json5)

    implementation(libs.kevinnzouWebview)
}