dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
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
//include(":shizuku_automator")
//include(":shizuku_automator:hidden_api")
