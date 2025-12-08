plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = rootProject.ext["android.namespace"].toString() + "." + project.name
    compileSdk = rootProject.ext["android.compileSdk"] as Int
    buildToolsVersion = rootProject.ext["android.buildToolsVersion"].toString()
    defaultConfig {
        minSdk = rootProject.ext["android.minSdk"] as Int
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.rikka.refine.annotation)
    annotationProcessor(libs.rikka.refine.processor)
}