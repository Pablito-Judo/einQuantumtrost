plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.worldpulse.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.worldpulse.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-s1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            ndk { abiFilters += "arm64-v8a" } // S23; Debug zusätzlich x86_64 für Emulator-Tests
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // privat, nur Sideload
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    androidResources { noCompress += "pbf" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("com.google.ai.edge.litertlm.ExperimentalApi")
    }
}

dependencies {
    implementation(project(":data"))
    implementation(libs.maplibre.android)
    implementation(libs.androidx.work)
    implementation(libs.litertlm.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
