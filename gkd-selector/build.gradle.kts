import li.gkd.gradle.readNpmDependencies

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    jvm {}
    js {
        outputModuleName = project.name
        compilerOptions {
            target.set("es2015")
        }
        binaries.executable()
        useEsModules()
        generateTypeScriptDefinitions()
        nodejs()
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.js.ExperimentalJsStatic")
            languageSettings.optIn("kotlin.js.ExperimentalJsCollectionsApi")
        }
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.json5)
            }
        }
        jsMain {
            dependencies {
                readNpmDependencies().forEach { dependency ->
                    implementation(npm(dependency.name, dependency.version))
                }
            }
        }
    }
}
