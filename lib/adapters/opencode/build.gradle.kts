plugins {
    id("openspec-module")
    id("openspec-publish")
}

dependencies {
    api(project(":lib:generators"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
