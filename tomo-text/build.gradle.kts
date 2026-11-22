import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Fanfic models and parsers without Android, shared by the app and Tomo for the computer
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(mihonx.plugins.spotless)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.jsoup)
    api(libs.okhttp.core)
    api(libs.kotlinx.serialization.json)
    // jsoup marks its types with these annotations but does not pass them on to the compiler
    compileOnly("org.jspecify:jspecify:1.0.0")
}
