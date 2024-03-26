plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_17.majorVersion
        }
    }
    wasmJs {
        binaries.executable()
        useEsModules()
        generateTypeScriptDefinitions()
        browser {}
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
        }
    }
    sourceSets["commonMain"].dependencies {
        implementation(libs.kotlin.stdlib.common)
    }
}
