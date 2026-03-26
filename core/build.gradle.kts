plugins {
    id("openspec-module")
    id("openspec-publish")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
