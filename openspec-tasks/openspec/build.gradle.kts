plugins {
    id("openspec-module")
    id("openspec-publish")
}

dependencies {
    api(project(":core"))
    api(project(":generators"))
    api(project(":exec"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
