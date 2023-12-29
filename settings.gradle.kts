rootProject.name = "gkd"
include(":app")
include(":selector")
include(":hidden_api")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}

dependencyResolutionManagement {

//    https://youtrack.jetbrains.com/issue/KT-55620
//    https://stackoverflow.com/questions/69163511
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }

    versionCatalogs {
        create("libs") {
            val kotlinVersion = "1.9.21"
            // use jdk17
            version("jdkVersion", JavaVersion.VERSION_17.majorVersion)
            version("kotlinVersion", kotlinVersion)

            version("android.compileSdk", "34")
            version("android.targetSdk", "34")
            version("android.buildToolsVersion", "34.0.0")
            version("android.minSdk", "26")

            val androidLibraryVersion = "8.2.0"
            library("android.gradle", "com.android.tools.build:gradle:$androidLibraryVersion")
            plugin("android.library", "com.android.library").version(androidLibraryVersion)
            plugin("android.application", "com.android.application").version(androidLibraryVersion)

            // 当前 android 项目 kotlin 的版本
            library(
                "kotlin.gradle.plugin", "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
            )
            library(
                "kotlin.serialization", "org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion"
            )
            library(
                "kotlin.stdlib.common", "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion"
            )
            plugin("kotlin.serialization", "org.jetbrains.kotlin.plugin.serialization").version(
                kotlinVersion
            )
            plugin("kotlin.parcelize", "org.jetbrains.kotlin.plugin.parcelize").version(
                kotlinVersion
            )
            plugin("kotlin.multiplatform", "org.jetbrains.kotlin.multiplatform").version(
                kotlinVersion
            )
            plugin("kotlin.android", "org.jetbrains.kotlin.android").version(kotlinVersion)

            // compose 编译器的版本, 需要注意它与 compose 的版本没有关联
            // https://mvnrepository.com/artifact/androidx.compose.compiler/compiler
            version("compose.compilerVersion", "1.5.7")
            val composeVersion = "1.5.4"
            library("compose.ui", "androidx.compose.ui:ui:$composeVersion")
            library("compose.preview", "androidx.compose.ui:ui-tooling-preview:$composeVersion")
            library("compose.tooling", "androidx.compose.ui:ui-tooling:$composeVersion")
            library("compose.junit4", "androidx.compose.ui:ui-test-junit4:$composeVersion")
            library("compose.material3", "androidx.compose.material3:material3:1.1.2")
            library("compose.activity", "androidx.activity:activity-compose:1.8.2")

            // https://github.com/Tencent/MMKV/blob/master/README_CN.md
            library("tencent.mmkv", "com.tencent:mmkv:1.3.2")
            // https://bugly.qq.com/docs/user-guide/instruction-manual-android/
            library("tencent.bugly", "com.tencent.bugly:crashreport:4.1.9.3")

            // https://github.com/RikkaApps/HiddenApiRefinePlugin
            val rikkaVersion = "4.4.0"
            plugin("rikka.refine", "dev.rikka.tools.refine").version(rikkaVersion)
            library("rikka.gradle", "dev.rikka.tools.refine:gradle-plugin:$rikkaVersion")
            library("rikka.processor", "dev.rikka.tools.refine:annotation-processor:$rikkaVersion")
            library("rikka.annotation", "dev.rikka.tools.refine:annotation:$rikkaVersion")
            library("rikka.runtime", "dev.rikka.tools.refine:runtime:$rikkaVersion")

            // https://github.com/RikkaApps/Shizuku-API
            library("rikka.shizuku.api", "dev.rikka.shizuku:api:13.1.5")
            library("rikka.shizuku.provider", "dev.rikka.shizuku:provider:13.1.5")

            // https://github.com/LSPosed/AndroidHiddenApiBypass
            library("lsposed.hiddenapibypass", "org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

            // 工具集合类
            // https://github.com/Blankj/AndroidUtilCode/blob/master/lib/utilcode/README-CN.md
            library("others.utilcodex", "com.blankj:utilcodex:1.31.1")

            // https://dylancaicoding.github.io/ActivityResultLauncher/#/
            library(
                "others.activityResultLauncher",
                "com.github.DylanCaiCoding:ActivityResultLauncher:1.1.2"
            )
            // json5
            // https://github.com/falkreon/Jankson
            library("others.jankson", "blue.endless:jankson:1.2.3")

            // https://github.com/TorryDo/Floating-Bubble-View
            library("others.floating.bubble.view", "io.github.torrydo:floating-bubble-view:0.6.3")

            library("androidx.appcompat", "androidx.appcompat:appcompat:1.6.1")
            library("androidx.core.ktx", "androidx.core:core-ktx:1.12.0")
            library(
                "androidx.lifecycle.runtime.ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.6.2"
            )
            library("androidx.junit", "androidx.test.ext:junit:1.1.5")
            library("androidx.espresso", "androidx.test.espresso:espresso-core:3.5.1")

            // https://developer.android.com/jetpack/androidx/releases/room
            val roomVersion = "2.6.1"
            library("androidx.room.runtime", "androidx.room:room-runtime:$roomVersion")
            library("androidx.room.compiler", "androidx.room:room-compiler:$roomVersion")
            library("androidx.room.ktx", "androidx.room:room-ktx:$roomVersion")

            library("androidx.splashscreen", "androidx.core:core-splashscreen:1.0.1")

            library(
                "google.accompanist.drawablepainter",
                "com.google.accompanist:accompanist-drawablepainter:0.32.0"
            )

            library("junit", "junit:junit:4.13.2")

            val ktorVersion = "2.3.7"
            // 请注意,当 client 和 server 版本不一致时, 会报错 socket hang up
            library("ktor.server.core", "io.ktor:ktor-server-core:$ktorVersion")
            library("ktor.server.cio", "io.ktor:ktor-server-cio:$ktorVersion")
            library("ktor.server.cors", "io.ktor:ktor-server-cors:$ktorVersion")
            library(
                "ktor.server.content.negotiation", "io.ktor:ktor-server-content-negotiation:$ktorVersion"
            )
            library("ktor.client.core", "io.ktor:ktor-client-core:$ktorVersion")
            library("ktor.client.okhttp", "io.ktor:ktor-client-okhttp:$ktorVersion")
//            https://ktor.io/docs/http-client-engines.html#android android 平台使用 android 或者 okhttp 都行
            library(
                "ktor.client.content.negotiation", "io.ktor:ktor-client-content-negotiation:$ktorVersion"
            )
            library(
                "ktor.serialization.kotlinx.json", "io.ktor:ktor-serialization-kotlinx-json:$ktorVersion"
            )

            library(
                "kotlinx.serialization.json",
                "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2"
            )

            // https://github.com/Kotlin/kotlinx.collections.immutable
            library(
                "kotlinx.collections.immutable",
                "org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7"
            )

//            https://developer.android.com/reference/kotlin/org/json/package-summary
            library("org.json", "org.json:json:20210307")

            plugin("google.ksp", "com.google.devtools.ksp").version("1.9.21-1.0.16")

            val hiltVersion = "2.50"
            plugin("google.hilt", "com.google.dagger.hilt.android").version(hiltVersion)
            library("google.hilt.android", "com.google.dagger:hilt-android:$hiltVersion")
            library(
                "google.hilt.android.compiler",
                "com.google.dagger:hilt-android-compiler:$hiltVersion"
            )
            library(
                "androidx.hilt.navigation.compose", "androidx.hilt:hilt-navigation-compose:1.1.0"
            )

//            https://github.com/raamcosta/compose-destinations
            val destinationsVersion = "1.9.56"
            library(
                "destinations.core",
                "io.github.raamcosta.compose-destinations:core:$destinationsVersion"
            )
            library(
                "destinations.ksp",
                "io.github.raamcosta.compose-destinations:ksp:$destinationsVersion"
            )

            val coilVersion = "2.5.0"
            library("coil.compose", "io.coil-kt:coil-compose:$coilVersion")
            library("coil.gif", "io.coil-kt:coil-gif:$coilVersion")

//            https://github.com/aclassen/ComposeReorderable
            library("others.reorderable", "org.burnoutcrew.composereorderable:reorderable:0.9.6")
        }
    }
}

