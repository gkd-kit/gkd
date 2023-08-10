plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_17.majorVersion
        }
    }
//    https://kotlinlang.org/docs/js-to-kotlin-interop.html#kotlin-types-in-javascript
    js(IR) {
        binaries.executable()
//        useEsModules()
//        bug example kotlin CharSequence.contains(char: Char) not work with js string.includes(string)
        generateTypeScriptDefinitions()
        browser {}
    }
    sourceSets["commonMain"].dependencies {
        implementation(libs.kotlin.stdlib.common)
    }
    sourceSets["jvmTest"].dependencies {
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.junit)
    }
}
