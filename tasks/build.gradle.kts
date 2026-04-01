plugins {
    id("openspec-module")
    id("openspec-publish")
}

dependencies {
    api(project(":core"))
    api(project(":psi"))
    api(project(":arch"))
    api(project(":exec"))
    api(project(":generators"))
    api(project(":quality"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
