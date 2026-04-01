plugins {
    id("openspec-plugin")
    id("openspec-publish")
}

dependencies {
    implementation(project(":core"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
