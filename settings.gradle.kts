dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven {
            url = uri("https://jitpack.io" )
        }
    }
}
pluginManagement {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
}

rootProject.name = "gkd"

include(":app")
include(":selector")
include(":room_processor")
include(":router")

