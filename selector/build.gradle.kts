import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
//    https://kotlinlang.org/docs/js-to-kotlin-interop.html#kotlin-types-in-javascript
    js(IR) {
        binaries.executable()
        useEsModules()
        generateTypeScriptDefinitions()
        browser {}
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
        }
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib.common)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlin.test)
            }
        }
    }
}
