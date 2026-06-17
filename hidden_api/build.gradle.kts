plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = project.name
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.remap.annotation)
    annotationProcessor(libs.remap.processor)
}