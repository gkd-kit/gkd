plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "li.gkd.aidl"
    buildFeatures.aidl = true
}
