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
import li.gkd.gradle.releaseBuildKey

val gitInfo = project.gitInfo

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.codeorigin)
}

android {
    namespace = "li.gkd.android"
    defaultConfig {
        applicationId = "li.songe.gkd"
        versionCode = rootProject.extra["gkdVersionCode"] as Int
        versionName = rootProject.extra["gkdVersionName"] as String

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
        resValues = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true

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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = gkdSigningConfig
            applicationIdSuffix = ".debug"
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

if (buildProperty("GKD_RENAME_PACKAGE_FLAG").isPresent) {
    androidComponents.onVariants { variant ->
        variant.outputs.onEach { output ->
            output as VariantOutputImpl
            output.outputFileName = "gkd-v${output.versionName.get()}.apk"
        }
    }
}

dependencies {
    implementation(project(":gkd-app"))
    // R8 needs hidden framework declarations to preserve system callback implementations.
    compileOnly(project(":gkd-hidden-api"))
    implementation(libs.rikka.shizuku.provider)
    debugImplementation(libs.compose.tooling)

    // These tests use the application manifest and gkd build variant.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric) {
        exclude(group = "org.robolectric", module = "nativeruntime-dist-compat")
    }
}
