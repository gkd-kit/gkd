import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlinx.atomicfu) apply false
    alias(libs.plugins.remap) apply false
    alias(libs.plugins.loc) apply false
    alias(libs.plugins.littlerobots.version)
}

object Cfg {
    val compileSdk get() = 37
    val buildToolsVersion get() = "37.0.0"
    val minSdk get() = 26
    val targetSdk get() = compileSdk
    val sourceVersion = JavaVersion.VERSION_11
    val targetVersion get() = sourceVersion
    val kotlinTargetVersion get() = JvmTarget.fromTarget(targetVersion.majorVersion)
    val kotlinCompilerArgs = listOf(
        "-opt-in=kotlin.RequiresOptIn",
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlinx.coroutines.FlowPreview",
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
        "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        "-XXLanguage:+MultiDollarInterpolation",
    )
}

subprojects {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(Cfg.kotlinTargetVersion)
        }
    }
    plugins.withType<AppPlugin> {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions {
                freeCompilerArgs.addAll(Cfg.kotlinCompilerArgs)
            }
        }
        extensions.getByType(ApplicationExtension::class.java).apply {
            compileSdk = Cfg.compileSdk
            buildToolsVersion = Cfg.buildToolsVersion
            defaultConfig {
                minSdk = Cfg.minSdk
                targetSdk = Cfg.targetSdk
            }
            compileOptions {
                sourceCompatibility = Cfg.sourceVersion
                targetCompatibility = Cfg.targetVersion
            }
        }
    }
    plugins.withType<LibraryPlugin> {
        extensions.getByType(LibraryExtension::class.java).apply {
            compileSdk = Cfg.compileSdk
            buildToolsVersion = Cfg.buildToolsVersion
            defaultConfig {
                minSdk = Cfg.minSdk
            }
            compileOptions {
                sourceCompatibility = Cfg.sourceVersion
                targetCompatibility = Cfg.targetVersion
            }
        }
    }
}
