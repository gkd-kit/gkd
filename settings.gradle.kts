rootProject.name = "gkd"
include(":app")
include(":selector")
include(":hidden_api")
include(":wasm_matches")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}
