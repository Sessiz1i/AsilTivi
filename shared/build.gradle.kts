plugins {
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        // Host JUnit tests consume commonTest through the existing Android target.
        val androidUnitTest by getting {
            dependencies { implementation(kotlin("test-junit")) }
        }
    }
}

dependencies { add("kspAndroid", libs.room.compiler) }

room { schemaDirectory("$projectDir/schemas") }

// Lock the host test and Android debug processor/tool graphs used by this slice.
configurations.configureEach {
    if (name in setOf(
            "debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath",
            "kspDebugKotlinAndroidProcessorClasspath", "kspDebugKotlinProcessorClasspath",
            "kspPluginClasspath", "kspPluginClasspathNonEmbeddable",
        )) {
        resolutionStrategy.activateDependencyLocking()
    }
}

android {
    namespace = "com.omer.belestiviclone.shared"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
