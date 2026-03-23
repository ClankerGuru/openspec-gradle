plugins {
    id("openspec-module")
}

dependencies {
    api(project(":psi"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
