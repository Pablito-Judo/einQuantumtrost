// Reine Kotlin/JVM-Bibliothek: Parser, Auswerter, Goldstandard, Benchmark-Hilfen.
// Wird von bench/ und worldpulse/ per includeBuild("../core") eingebunden.
plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    `java-library`
}

group = "de.worldpulse"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test"))
    testImplementation("com.networknt:json-schema-validator:1.5.9")
}

tasks.test { useJUnitPlatform() }
