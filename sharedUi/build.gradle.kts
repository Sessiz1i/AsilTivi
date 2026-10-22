plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvmToolchain(17)
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        val androidUnitTest by getting { dependencies { implementation(kotlin("test-junit")) } }
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
    }
}

android {
    namespace = "com.omer.belestiviclone.sharedui"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


configurations.configureEach { if (name in setOf("debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath")) resolutionStrategy.activateDependencyLocking() }

