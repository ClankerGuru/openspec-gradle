plugins {
    id("openspec-module")
}

dependencies {
    api(project(":generators"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
