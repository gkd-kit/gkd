import li.gkd.gradle.CollectConsumerProguardRulesTask
import li.gkd.gradle.ValidateStringResourcesTask
import li.gkd.gradle.buildProperty

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.cmp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.codeorigin)
    alias(libs.plugins.remap)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "li.gkd.app.resources"
}

val validateStringResources =
    tasks.register<ValidateStringResourcesTask>("validateStringResources") {
        resourceDirectory.set(layout.projectDirectory.dir("src/commonMain/composeResources"))
    }

tasks.matching { it.name == "prepareComposeResourcesTaskForCommonMain" }.configureEach {
    dependsOn(validateStringResources)
}

kotlin {
    android {
        namespace = "li.gkd.app"
        androidResources.enable = true
        withHostTest {}
    }
    jvm()
    sourceSets {
        commonMain.dependencies {
            compileOnly(libs.codeorigin)
            api(libs.cmp.resources)
            api(project(":gkd-db"))
            api(project(":gkd-selector"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.atomicfu)
            api(libs.exp4j)
            api(libs.androidx.paging.compose)
            api(libs.coil.compose)
            api(libs.coil.network)
            api(libs.ktor.client.okhttp)
            api(libs.ktor.server.core)
            api(libs.ktor.server.cio)
            api(libs.ktor.server.content.negotiation)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.telephoto.zoomable)
            api(libs.reorderable)
            api(libs.kmp.viewmodel)
            api(libs.kmp.lifecycle)
            api(libs.kmp.navigation3)
            api(libs.androidx.navigation3.runtime)
            api(libs.kmp.viewmodel.navigation3)
            // AndroidX Runtime provides real implementations for both Android and JVM.
            api(libs.compose.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.ui.graphics)
            api(libs.cmp.animation)
            api(libs.cmp.material3)
            api(libs.cmp.icons)
            api(libs.morph.compose)
            api(libs.priv.kit.ui)
            api(libs.json5)
            api(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            api(project(":gkd-aidl"))
            api(libs.androidx.lifecycle.service)
            api(libs.compose.activity)
            api(libs.rikka.shizuku.api)
            api(libs.lsposed.hiddenapibypass)
            api(libs.google.accompanist.drawablepainter)
            api(libs.androidx.splashscreen)
            api(libs.coil.gif)
            api(libs.toaster)
            api(libs.permissions)
            api(libs.device)
            api(libs.androidx.core.ktx)
            // Control Android Compose independently of CMP; export the same versions to the app.
            api(libs.compose.animation)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            api(libs.compose.ui.graphics)
        }
        jvmMain.dependencies {
            api(libs.jna)
            api(libs.jna.platform)
            api(libs.webview2.compose)
            api(compose.desktop.currentOs)
            api(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

dependencies {
    remapApi(project(":gkd-hidden-api"))
}

val collectDesktopConsumerRules =
    tasks.register<CollectConsumerProguardRulesTask>("collectDesktopConsumerRules") {
        group = "compose desktop"
        description = "Collects META-INF/proguard/*.pro from JVM runtime dependencies."
        runtimeJars.from(configurations.named("jvmRuntimeClasspath").map { classpath ->
            classpath.filter { it.extension.equals("jar", ignoreCase = true) }
        })
        rulesFile.set(layout.buildDirectory.file("generated/proguard/desktop-consumer-rules.pro"))
    }

compose.desktop.application {
    mainClass = "li.gkd.app.DesktopMainKt"
    nativeDistributions {
        packageName = "GKD"
        packageVersion = (rootProject.extra["gkdVersionName"] as String).substringBefore('-')
        description = "GKD Desktop Host"
        windows {
            iconFile.set(project.file("src/jvmMain/resources/gkd.ico"))
        }
        // suggestModules plus dynamic JDBC/naming providers used by runtime dependencies.
        modules("java.instrument", "java.management", "jdk.unsupported", "java.sql", "java.naming")
    }
    buildTypes.release.proguard {
        version.set(libs.versions.proguard)
        configurationFiles.from(project.file("desktop.pro"))
        configurationFiles.from(collectDesktopConsumerRules.flatMap { it.rulesFile })
        // Preserve reflection/JNI identities and avoid optimizer changes to Compose bytecode.
        obfuscate.set(false)
        optimize.set(false)
    }
}

tasks.withType<JavaExec>().matching { it.name == "run" || it.name == "runRelease" }
    .configureEach {
        systemProperty("gkd.projectRoot", rootProject.projectDir.absolutePath)
    }

if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
    val archiveBaseName = if (buildProperty("GKD_RENAME_PACKAGE_FLAG").isPresent) {
        "gkd-v${rootProject.extra["gkdVersionName"]}"
    } else {
        "gkd"
    }
    val archiveArchitecture = when (val architecture = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        else -> architecture
    }
    tasks.register<Zip>("packageWindowsPortable") {
        group = "compose desktop"
        description = "Packages the Windows application and Java runtime as a portable ZIP."
        from(tasks.named("createReleaseDistributable"))
        archiveFileName.set("$archiveBaseName.win-$archiveArchitecture.zip")
        destinationDirectory.set(rootProject.layout.projectDirectory.dir(".local/desktop-packages"))
    }
}
