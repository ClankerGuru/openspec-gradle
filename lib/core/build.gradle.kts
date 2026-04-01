plugins {
    id("openspec-module")
    id("openspec-publish")
    alias(libs.plugins.kotlin.serialization)
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("openspec-gradle.properties") {
        expand(props)
    }
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
