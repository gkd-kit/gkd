rootProject.name = "gkd"
include(":app")
include(":router")
include(":selector_core")
include(":selector_android")

pluginManagement {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
}

dependencyResolutionManagement {
//    https://youtrack.jetbrains.com/issue/KT-55620
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
    versionCatalogs {
        create("libs") {
            library("android.gradle", "com.android.tools.build:gradle:7.3.1")

            version("android.compileSdk", "33")
            version("android.minSdk", "26")
            version("android.targetSdk", "33")
            version("android.buildToolsVersion", "33.0.0")

            // 当前 android 项目 kotlin 的版本
            library("kotlin.gradle.plugin", "org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
            library("kotlin.serialization", "org.jetbrains.kotlin:kotlin-serialization:1.8.20")
//            library("kotlin.stdlib", "org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

            // compose 编译器的版本, 需要注意它与 compose 的版本没有关联
            // https://mvnrepository.com/artifact/androidx.compose.compiler/compiler
            version("compose.compiler", "1.4.6")
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

            library("rikka.gradle", "dev.rikka.tools.refine:gradle-plugin:3.0.3")
            library("rikka.shizuku.api", "dev.rikka.shizuku:api:12.1.0")
            library("rikka.shizuku.provider", "dev.rikka.shizuku:provider:12.1.0")

            // 工具集合类
            // https://github.com/Blankj/AndroidUtilCode/blob/master/lib/utilcode/README-CN.md
            library("others.utilcodex", "com.blankj:utilcodex:1.31.0")
            // https://dylancaicoding.github.io/ActivityResultLauncher/#/
            library(
                "others.ActivityResultLauncher",
                "com.github.DylanCaiCoding:ActivityResultLauncher:1.1.2"
            )
            // https://github.com/falkreon/Jankson
            library("others.jankson", "blue.endless:jankson:1.2.1")
            // https://github.com/journeyapps/zxing-android-embedded
            library("others.zxing.android.embedded", "com.journeyapps:zxing-android-embedded:4.3.0")
            library("others.floating.bubble.view", "io.github.torrydo:floating-bubble-view:0.5.2")


            library("androidx.localbroadcastmanager", "androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
            library("androidx.appcompat", "androidx.appcompat:appcompat:1.6.1")
            library("androidx.core.ktx", "androidx.core:core-ktx:1.10.0")
            library(
                "androidx.lifecycle.runtime.ktx",
                "androidx.lifecycle:lifecycle-runtime-ktx:2.6.1"
            )
            library("androidx.junit", "androidx.test.ext:junit:1.1.5")
            library("androidx.espresso", "androidx.test.espresso:espresso-core:3.5.1")

            // https://developer.android.google.cn/training/data-storage/room?hl=zh-cn
            library("androidx.room.runtime", "androidx.room:room-runtime:2.5.1")
            library("androidx.room.compiler", "androidx.room:room-compiler:2.5.1")
            library("androidx.room.ktx", "androidx.room:room-ktx:2.5.1")

            library(
                "google.accompanist.drawablepainter",
                "com.google.accompanist:accompanist-drawablepainter:0.23.1"
            )
            library(
                "google.accompanist.placeholder.material",
                "com.google.accompanist:accompanist-placeholder-material:0.23.1"
            )

            library("junit", "junit:junit:4.13.2")

            // 请注意,当 client 和 server 版本不一致时, 会报错 socket hang up
            library("ktor.server.core", "io.ktor:ktor-server-core:2.2.3")
            library("ktor.server.netty", "io.ktor:ktor-server-netty:2.2.3")
            library("ktor.server.cors", "io.ktor:ktor-server-cors:2.2.3")
            library(
                "ktor.server.content.negotiation",
                "io.ktor:ktor-server-content-negotiation:2.2.3"
            )
            library("ktor.client.core", "io.ktor:ktor-client-core:2.2.3")
            library("ktor.client.cio", "io.ktor:ktor-client-cio:2.2.3")
            library(
                "ktor.client.content.negotiation",
                "io.ktor:ktor-client-content-negotiation:2.2.3"
            )
            library(
                "ktor.serialization.kotlinx.json",
                "io.ktor:ktor-serialization-kotlinx-json:2.2.3"
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
        }
    }
}

