plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "hidden_api"
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.remap.annotation)
    annotationProcessor(libs.remap.processor)
}
