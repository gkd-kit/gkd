import nl.littlerobots.vcu.plugin.versionSelector
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

ext {
    set("android.namespace", "li.songe.gkd")
    set("android.buildToolsVersion", "37.0.0")
    set("android.compileSdk", 37)
    set("android.targetSdk", 37)
    set("android.minSdk", 26)
    set("android.javaVersion", JavaVersion.VERSION_11)
    set("kotlin.jvmTarget", JvmTarget.JVM_11)
}

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

// ./gradlew versionCatalogUpdate --interactive
versionCatalogUpdate {
    versionSelector {
        val a = it.currentVersion
        val b = it.candidate.version
        isSameTypeVersion(a, b) && isNewerVersion(a, b)
    }
}
projectDir.resolve("./gradle/libs.versions.updates.toml").apply {
    if (exists()) {
        delete()
    }
}

val versionReg = "^[0-9\\.]+".toRegex()
fun isSameTypeVersion(currentVersion: String, newVersion: String): Boolean {
    if (versionReg.matches(currentVersion)) {
        return versionReg.matches(newVersion)
    }
    arrayOf("alpha", "beta", "dev", "rc").forEach { v ->
        if (currentVersion.contains(v, true)) {
            return newVersion.contains(v, true)
        }
    }
    throw IllegalArgumentException("Unknown version type: $currentVersion -> $newVersion")
}

val numberReg = "\\d+".toRegex()
fun isNewerVersion(currentVersion: String, newVersion: String): Boolean {
    val currentParts = numberReg.findAll(currentVersion).map { it.value.toInt() }.toList()
    val newParts = numberReg.findAll(newVersion).map { it.value.toInt() }.toList()
    val length = maxOf(currentParts.size, newParts.size)
    for (i in 0 until length) {
        val currentPart = currentParts.getOrNull(i) ?: 0
        val newPart = newParts.getOrNull(i) ?: 0
        if (currentPart < newPart) {
            return true
        } else if (currentPart > newPart) {
            return false
        }
    }
    return false
}
