plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.worldpulse.bench"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.worldpulse.bench"
        minSdk = 31          // S23 läuft mit Android 15/16; 31 reicht für ExitInfo/ThermalHeadroom
        targetSdk = 36       // >= 35 nötig, damit die Android-15-FGS-Zeitlimits greifen (Test 9)
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // privat, nur Sideload
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        // Modelle NICHT ins APK; native Libs unkomprimiert lassen (schnelleres Laden).
        jniLibs { useLegacyPackaging = false }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("com.google.ai.edge.litertlm.ExperimentalApi")
    }
}

dependencies {
    implementation(libs.worldpulse.core)
    implementation(libs.litertlm.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
}
