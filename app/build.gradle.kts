import com.android.build.api.variant.impl.VariantOutputImpl
import javax.xml.parsers.DocumentBuilderFactory
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
) {
    val versionNameSuffix get() = if (tagName == null) ("-" + commitId.take(7)) else null
}

val gitInfo = GitInfo(
    commitId = "git rev-parse HEAD".runCommand(),
    commitTime = "git log -1 --format=%ct".runCommand() + "000",
    tagName = runCatching { "git describe --tags --exact-match".runCommand() }.getOrNull(),
)

val debugSuffixPairList by lazy {
    DocumentBuilderFactory
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
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.remap)
    alias(libs.plugins.loc)
}

android {
    namespace = "li.songe.gkd"
    defaultConfig {
        applicationId = "li.songe.gkd"
        versionCode = 92
        versionName = "1.12.1"

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
        resValues = true
    }

    val gkdSigningConfig = if (project.hasProperty("GKD_STORE_FILE")) {
        signingConfigs.create("gkd") {
            storeFile = file(project.properties["GKD_STORE_FILE"] as String)
            storePassword = project.findProperty("GKD_STORE_PASSWORD")?.toString()
            keyAlias = project.findProperty("GKD_KEY_ALIAS")?.toString()
            keyPassword = project.findProperty("GKD_KEY_PASSWORD")?.toString()
        }
    } else {
        signingConfigs.getByName("debug")
    }

    val playSigningConfig = if (project.hasProperty("PLAY_STORE_FILE")) {
        signingConfigs.create("play") {
            storeFile = file(project.properties["PLAY_STORE_FILE"].toString())
            storePassword = project.properties["PLAY_STORE_PASSWORD"].toString()
            keyAlias = project.properties["PLAY_KEY_ALIAS"].toString()
            keyPassword = project.properties["PLAY_KEY_PASSWORD"].toString()
        }
    } else {
        gkdSigningConfig
    }

    buildTypes {
        all {
            versionNameSuffix = gitInfo.versionNameSuffix
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
            resValue("color", "better_black", "#FF5D92")
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
            signingConfig = playSigningConfig
            resValue("bool", "is_accessibility_tool", "false")
        }
        all {
            dimension = flavorDimensions.first()
            manifestPlaceholders["channel"] = name
        }
    }
    dependenciesInfo.includeInApk = false
    packaging.resources.excludes += setOf(
        // https://github.com/Kotlin/kotlinx.coroutines/issues/2023
        "META-INF/**", "**/attach_hotspot_windows.dll",

        "**.properties", "**.bin", "**/*.proto",
        "**/kotlin-tooling-metadata.json",

        // ktor
        "**/custom.config.conf",
        "**/custom.config.yaml",
    )
}

if (project.hasProperty("GKD_RENAME_APK_FLAG")) {
    androidComponents.onVariants { variant ->
        variant.outputs.onEach { output ->
            output as VariantOutputImpl
            output.outputFileName = "gkd-v${output.versionName.get()}.apk"
        }
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

loc {
    template = "{packageName}.{methodName}({fileName}:{lineNumber})"
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

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    compileOnly(project(":hidden_api"))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
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

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    // https://github.com/Kotlin/kotlinx-atomicfu/issues/145
    implementation(libs.kotlinx.atomicfu)

    implementation(libs.activityResultLauncher)

    implementation(libs.reorderable)

    implementation(libs.androidx.splashscreen)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.zoomable)

    implementation(libs.exp4j)

    implementation(libs.toaster)
    implementation(libs.permissions)
    implementation(libs.device)

    implementation(libs.json5)
    compileOnly(libs.loc.annotation)

    implementation(libs.kevinnzouWebview)
}
