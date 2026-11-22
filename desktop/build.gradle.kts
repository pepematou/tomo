import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Tomo for the computer (phase 4, first stage): library from the sync folder, fanfics and the tray
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
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
    implementation(projects.tomoText)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "tomo.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            modules("java.instrument", "java.net.http", "jdk.unsupported")
            packageName = "tomo"
            // The release passes the tag's version; jpackage wants MAJOR.MINOR.PATCH
            packageVersion = findProperty("desktopVersion") as String? ?: "0.25.0"
            description = "Tomo: tu biblioteca y fanfics en la computadora"
            linux {
                menuGroup = "Office"
            }
        }
    }
}
