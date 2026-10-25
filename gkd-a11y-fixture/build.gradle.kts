import li.gkd.gradle.getOrCreateSigningConfigs

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "li.gkd.a11y.fixture"
    defaultConfig {
        applicationId = "li.gkd.a11y.fixture"
    }
    val signing = project.getOrCreateSigningConfigs(signingConfigs)
    buildTypes {
        debug {
            signingConfig = signing.gkd
            vcsInfo.include = false
        }
    }
}
