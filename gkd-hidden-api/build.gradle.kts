plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "li.gkd.hidden.api"
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.remap.annotation)
    annotationProcessor(libs.remap.processor)
}
