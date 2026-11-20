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
    // suspend-Funktionen im Extraktor (LLM-Aufruf); Tests nutzen runBlocking
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // S2 Scout: HTML-Bereinigung (deterministisch, ohne JavaScript) und Quellenregister sources.yaml
    api("org.jsoup:jsoup:1.24.1")
    implementation("com.charleskorn.kaml:kaml:0.104.0")
    testImplementation(kotlin("test"))
    testImplementation("com.networknt:json-schema-validator:1.5.9")
}

tasks.test { useJUnitPlatform() }

// Live-Probe der Quellen (nur mit Internet, z. B. im CI): ./gradlew liveProbe
tasks.register<JavaExec>("liveProbe") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("de.worldpulse.core.scout.LiveProbeKt")
}
