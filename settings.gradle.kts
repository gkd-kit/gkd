pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeVersionByRegex(".*", ".*", ".*-SNAPSHOT") }
        }
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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gkd"

include(
    ":gkd-android",
    ":gkd-a11y-fixture",
    ":gkd-aidl",
    ":gkd-app",
    ":gkd-db",
    ":gkd-hidden-api",
    ":gkd-selector",
)

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeVersionByRegex(".*", ".*", ".*-SNAPSHOT") }
        }
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://jitpack.io")
    }
}
