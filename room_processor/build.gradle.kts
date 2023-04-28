plugins {
    id("kotlin")
    kotlin("jvm")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=com.google.devtools.ksp.KspExperimental"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs = freeCompilerArgs + "-Xjvm-default=enable"
    }
}

dependencies{
    //ksp依赖
    implementation("com.google.devtools.ksp:symbol-processing-api:1.8.10-1.0.9")
    //poet依赖
    implementation("com.squareup:kotlinpoet:1.12.0")
    implementation("com.squareup:kotlinpoet-ksp:1.12.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
