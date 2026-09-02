import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.impl.VariantOutputImpl
import li.gkd.gradle.BuildAssetAdapter
import li.gkd.gradle.BuildAssetVariant
import li.gkd.gradle.GenerateSourcePathsTask
import li.gkd.gradle.buildProperty
import li.gkd.gradle.configureBuildAssets
import li.gkd.gradle.gitInfo
import li.gkd.gradle.readDebugSuffixResources
import li.gkd.gradle.releaseBuildKey

val gitInfo = project.gitInfo
val debugSuffixResources = project.readDebugSuffixResources()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.remap)
    alias(libs.plugins.codeorigin)
}

android {
    namespace = "li.gkd.app"
    defaultConfig {
        applicationId = "li.songe.gkd"
        versionCode = 92
        versionName = "1.12.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            localeFilters += listOf("zh-rCN", "en")
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        manifestPlaceholders["buildKey"] = ""
        manifestPlaceholders["commitId"] = gitInfo.commitId
        manifestPlaceholders["commitTime"] = gitInfo.commitTime
        manifestPlaceholders["tagName"] = gitInfo.tagName.orEmpty()
    }

    buildFeatures {
        compose = true
        aidl = true
        resValues = true
    }

    val gkdStoreFile = buildProperty("GKD_STORE_FILE").orNull
    val gkdSigningConfig = if (gkdStoreFile != null) {
        signingConfigs.create("gkd") {
            storeFile = file(gkdStoreFile)
            storePassword = buildProperty("GKD_STORE_PASSWORD").orNull
            keyAlias = buildProperty("GKD_KEY_ALIAS").orNull
            keyPassword = buildProperty("GKD_KEY_PASSWORD").orNull
        }
    } else {
        signingConfigs.getByName("debug")
    }

    val playStoreFile = buildProperty("PLAY_STORE_FILE").orNull
    val playSigningConfig = if (playStoreFile != null) {
        signingConfigs.create("play") {
            storeFile = file(playStoreFile)
            storePassword = buildProperty("PLAY_STORE_PASSWORD").orNull
            keyAlias = buildProperty("PLAY_KEY_ALIAS").orNull
            keyPassword = buildProperty("PLAY_KEY_PASSWORD").orNull
        }
    } else {
        gkdSigningConfig
    }

    buildTypes {
        all {
            vcsInfo.include = false
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
            for ((name, value) in debugSuffixResources) {
                resValue("string", name, value)
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
    // https://github.com/LSPosed/AndroidHiddenApiBypass
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // https://priv-kit.pages.dev/zh/guide/getting-started#native-library-packaging
    packaging.jniLibs.useLegacyPackaging = true
    packaging.resources.excludes += setOf(
        "META-INF/**",
        "DebugProbesKt.bin",
    )
}

val androidBuildAssetAdapter =
    BuildAssetAdapter<ApplicationAndroidComponentsExtension, ApplicationVariant>(
        onVariants = { components, buildType, action ->
            val selector = if (buildType == null) {
                components.selector().all()
            } else {
                components.selector().withBuildType(buildType)
            }
            components.onVariants(selector, action)
        },
        addGeneratedSourceDirectory = { variant, task ->
            variant.sources.assets?.addGeneratedSourceDirectory(
                task,
                GenerateSourcePathsTask::outputDirectory,
            )
        },
        describe = { variant ->
            val flavorName = variant.productFlavors
                .single { it.first == "channel" }
                .second
            val mainOutput = variant.outputs.single()
            BuildAssetVariant(
                name = variant.name,
                flavor = flavorName,
                buildType = requireNotNull(variant.buildType),
                mappingFile = variant.artifacts.get(
                    SingleArtifact.OBFUSCATION_MAPPING_FILE,
                ),
                versionCode = mainOutput.versionCode,
                versionName = mainOutput.versionName,
            )
        },
        computeTaskName = { variant, action, subject ->
            variant.computeTaskName(action, subject)
        },
    )

configureBuildAssets(
    androidComponents = androidComponents,
    adapter = androidBuildAssetAdapter,
)

androidComponents.onVariants(
    androidComponents.selector().withBuildType("release"),
) { variant ->
    val flavorName = variant.productFlavors
        .single { it.first == "channel" }
        .second
    variant.manifestPlaceholders.put(
        "buildKey",
        project.releaseBuildKey(flavorName, gitInfo.commitId),
    )
}

if (buildProperty("GKD_RENAME_APK_FLAG").isPresent) {
    androidComponents.onVariants { variant ->
        variant.outputs.onEach { output ->
            output as VariantOutputImpl
            output.outputFileName = "gkd-v${output.versionName.get()}.apk"
        }
    }
}

composeCompiler {
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
    }
    stabilityConfigurationFiles.addAll(
        rootProject.layout.projectDirectory.file("stability_config.conf"),
    )
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(project(":gkd-db"))
    implementation(project(":gkd-selector"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
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

    // AndroidTest shares this runtime dependency with the app and requires the newer version.
    implementation(libs.androidx.concurrent.futures)

    remapApi(project(":gkd-hidden-api"))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.priv.kit.ui)
    implementation(libs.lsposed.hiddenapibypass)

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
    compileOnly(libs.codeorigin)

    // compose-webview declares Material but does not use it.
    implementation(libs.kevinnzouWebview) {
        exclude(group = "com.google.android.material", module = "material")
    }
}
