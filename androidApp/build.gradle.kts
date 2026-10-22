plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.omer.belestiviclone"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.omer.belestiviclone"
        minSdk = 23
        targetSdk = 36
        versionCode = 61
        versionName = "0.6.22-preview-r42-four"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("../araclar/android-user-home/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("debug") } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":sharedUi"))
    implementation(project(":shared"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.androidx.foundation)
    implementation(libs.activity.compose)
    testImplementation(kotlin("test-junit"))
}

// Match KGP 2.2.21's KMP platform-compilation boundary in this Android consumer.
// The common-only shim has no JVM artifact; stdlib remains locked at 2.2.21.
// commonMain metadata in shared/sharedUi keeps its own stdlib-common dependency.
configurations.configureEach {
    if (name == "debugCompileClasspath" || name == "debugRuntimeClasspath") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    }
}


