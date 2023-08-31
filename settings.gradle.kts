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
            val kotlinVersion = "1.8.20"
            // use jdk17
            version("jdkVersion", JavaVersion.VERSION_17.majorVersion)
            version("kotlinVersion", kotlinVersion)

            version("android.compileSdk", "34")
            version("android.targetSdk", "34")
            version("android.buildToolsVersion", "34.0.0")
            version("android.minSdk", "26")

            library("android.gradle", "com.android.tools.build:gradle:8.1.0")
            plugin("android.library", "com.android.library").version("8.1.0")
            plugin("android.application", "com.android.application").version("8.1.0")

            // 当前 android 项目 kotlin 的版本
            library("kotlin.gradle.plugin", "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
            library("kotlin.serialization", "org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
            library("kotlin.stdlib.common", "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion")
            plugin("kotlin.serialization", "org.jetbrains.kotlin.plugin.serialization").version(kotlinVersion)
            plugin("kotlin.parcelize", "org.jetbrains.kotlin.plugin.parcelize").version(kotlinVersion)
            plugin("kotlin.kapt", "org.jetbrains.kotlin.kapt").version(kotlinVersion)
            plugin("kotlin.multiplatform", "org.jetbrains.kotlin.multiplatform").version(kotlinVersion)
            plugin("kotlin.android", "org.jetbrains.kotlin.android").version(kotlinVersion)

            // compose 编译器的版本, 需要注意它与 compose 的版本没有关联
            // https://mvnrepository.com/artifact/androidx.compose.compiler/compiler
            version("compose.compilerVersion", "1.4.6")
            library("compose.ui", "androidx.compose.ui:ui:1.4.2")
            library("compose.material", "androidx.compose.material:material:1.4.2")
            library("compose.preview", "androidx.compose.ui:ui-tooling-preview:1.4.2")
            library("compose.tooling", "androidx.compose.ui:ui-tooling:1.4.2")
            library("compose.junit4", "androidx.compose.ui:ui-test-junit4:1.4.2")
            library("compose.activity", "androidx.activity:activity-compose:1.7.0")

            // https://github.com/Tencent/MMKV/blob/master/README_CN.md
            library("tencent.mmkv", "com.tencent:mmkv:1.2.13")
            // https://bugly.qq.com/docs/user-guide/instruction-manual-android/
            library("tencent.bugly", "com.tencent.bugly:crashreport:4.0.4")

            // https://github.com/RikkaApps/HiddenApiRefinePlugin
            plugin("rikka.refine", "dev.rikka.tools.refine").version("4.3.0")
            library("rikka.gradle", "dev.rikka.tools.refine:gradle-plugin:4.3.0")
            library("rikka.processor", "dev.rikka.tools.refine:annotation-processor:4.3.0")
            library("rikka.annotation", "dev.rikka.tools.refine:annotation:4.3.0")
            library("rikka.runtime", "dev.rikka.tools.refine:runtime:4.3.0")

            // https://github.com/RikkaApps/Shizuku-API
            library("rikka.shizuku.api", "dev.rikka.shizuku:api:13.1.2")
            library("rikka.shizuku.provider", "dev.rikka.shizuku:provider:13.1.2")

            // https://github.com/LSPosed/AndroidHiddenApiBypass
            library("lsposed.hiddenapibypass", "org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

            // 工具集合类
            // https://github.com/Blankj/AndroidUtilCode/blob/master/lib/utilcode/README-CN.md
            library("others.utilcodex", "com.blankj:utilcodex:1.31.0")
            // https://dylancaicoding.github.io/ActivityResultLauncher/#/
            library(
                "others.activityResultLauncher",
                "com.github.DylanCaiCoding:ActivityResultLauncher:1.1.2"
            )
            // https://github.com/falkreon/Jankson
            library("others.jankson", "blue.endless:jankson:1.2.1")
            // https://github.com/journeyapps/zxing-android-embedded
            library("others.zxing.android.embedded", "com.journeyapps:zxing-android-embedded:4.3.0")
            // https://github.com/TorryDo/Floating-Bubble-View
            library("others.floating.bubble.view", "io.github.torrydo:floating-bubble-view:0.5.6")

            library("androidx.appcompat", "androidx.appcompat:appcompat:1.6.1")
            library("androidx.core.ktx", "androidx.core:core-ktx:1.10.0")
            library(
                "androidx.lifecycle.runtime.ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.6.1"
            )
            library("androidx.junit", "androidx.test.ext:junit:1.1.5")
            library("androidx.espresso", "androidx.test.espresso:espresso-core:3.5.1")

            // https://developer.android.google.cn/training/data-storage/room?hl=zh-cn
            library("androidx.room.runtime", "androidx.room:room-runtime:2.5.1")
            library("androidx.room.compiler", "androidx.room:room-compiler:2.5.1")
            library("androidx.room.ktx", "androidx.room:room-ktx:2.5.1")

            library("androidx.splashscreen", "androidx.core:core-splashscreen:1.0.1")

            library(
                "google.accompanist.drawablepainter",
                "com.google.accompanist:accompanist-drawablepainter:0.23.1"
            )
            library(
                "google.accompanist.placeholder.material",
                "com.google.accompanist:accompanist-placeholder-material:0.23.1"
            )

//            https://google.github.io/accompanist/systemuicontroller/
            library(
                "google.accompanist.systemuicontroller",
                "com.google.accompanist:accompanist-systemuicontroller:0.30.1"
            )

            library("junit", "junit:junit:4.13.2")

            // 请注意,当 client 和 server 版本不一致时, 会报错 socket hang up
            library("ktor.server.core", "io.ktor:ktor-server-core:2.3.1")
            library("ktor.server.netty", "io.ktor:ktor-server-netty:2.3.1")
            library("ktor.server.cors", "io.ktor:ktor-server-cors:2.3.1")
            library(
                "ktor.server.content.negotiation", "io.ktor:ktor-server-content-negotiation:2.3.1"
            )
            library("ktor.client.core", "io.ktor:ktor-client-core:2.3.1")
            library("ktor.client.okhttp", "io.ktor:ktor-client-okhttp:2.3.1")
//            https://ktor.io/docs/http-client-engines.html#android android 平台使用 android 或者 okhttp 都行
            library(
                "ktor.client.content.negotiation", "io.ktor:ktor-client-content-negotiation:2.3.1"
            )

            library(
                "ktor.serialization.kotlinx.json", "io.ktor:ktor-serialization-kotlinx-json:2.2.3"
            )

            library(
                "kotlinx.serialization.json",
                "org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1"
            )
            // https://github.com/Kotlin/kotlinx.collections.immutable
            library(
                "kotlinx.collections.immutable",
                "org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5"
            )

//            https://developer.android.com/reference/kotlin/org/json/package-summary
            library("org.json", "org.json:json:20210307")

            plugin("google.ksp", "com.google.devtools.ksp").version("1.8.20-1.0.11")

            plugin("google.hilt", "com.google.dagger.hilt.android").version("2.44")
            library("google.hilt.android", "com.google.dagger:hilt-android:2.44")
            library("google.hilt.android.compiler", "com.google.dagger:hilt-android-compiler:2.44")
            library(
                "androidx.hilt.navigation.compose", "androidx.hilt:hilt-navigation-compose:1.0.0"
            )

//            https://composedestinations.rafaelcosta.xyz/setup
            library(
                "destinations.core", "io.github.raamcosta.compose-destinations:core:1.9.52"
            )
            library("destinations.ksp", "io.github.raamcosta.compose-destinations:ksp:1.9.52")
//            library(
//                "destinations.animations",
//                "io.github.raamcosta.compose-destinations:animations-core:1.9.52"
//            )

//            https://github.com/aclassen/ComposeReorderable
            library("others.reorderable", "org.burnoutcrew.composereorderable:reorderable:0.9.6")
        }
    }
}

