plugins {
//    id("com.android.library")
    id("kotlin")
//    id("java")
    kotlin("jvm")
//    id("java-library")
//    id("org.jetbrains.kotlin.jvm")
//    application
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
    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.20-1.0.7")
    //poet依赖
    implementation("com.squareup:kotlinpoet:1.11.0")
    implementation("com.squareup:kotlinpoet-ksp:1.11.0")
//    implementation(kotlin("stdlib"))
//    implementation(kotlin("reflect"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
