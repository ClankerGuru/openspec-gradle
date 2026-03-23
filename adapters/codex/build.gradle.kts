plugins {
    id("openspec-module")
    id("openspec-publish")
}

dependencies {
    api(project(":generators"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
