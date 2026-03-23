plugins {
    id("openspec-module")
    id("openspec-publish")
}

dependencies {
    api(project(":core"))
    api(project(":psi"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
