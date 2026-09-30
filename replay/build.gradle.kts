import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:telemetry"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    jvmArgs("-XX:-DoEscapeAnalysis")
}
