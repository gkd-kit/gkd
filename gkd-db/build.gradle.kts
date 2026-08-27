import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.ksp)
}

kotlin {
    android {
        namespace = "li.gkd.db"
    }
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                api(libs.androidx.room.runtime)
                api(libs.androidx.room.paging)
                api(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.sqlite.framework)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.kotlin.test)
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "room.schemaDirectory",
        layout.projectDirectory.dir("schemas").asFile.absolutePath,
    )
}
