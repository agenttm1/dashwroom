import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jmh)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    jmh(project(":core:telemetry"))
    jmh(project(":replay"))
}

jmh {
    jmhVersion.set(libs.versions.jmh)
    warmupIterations.set(3)
    warmup.set("2s")
    iterations.set(5)
    timeOnIteration.set("2s")
    fork.set(1)
    timeUnit.set("us")
    benchmarkMode.set(listOf("avgt"))
    profilers.set(listOf("gc"))
    // ART performs no escape analysis, so measure allocations the way Android would see them.
    jvmArgsAppend.set(listOf("-XX:-DoEscapeAnalysis"))
    resultFormat.set("JSON")
}
