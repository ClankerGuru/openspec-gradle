plugins {
    id("openspec-plugin")
    id("openspec-publish")
}

dependencies {
    implementation(project(":lib:core"))
    implementation(project(":lib:generators"))
    implementation(project(":lib:exec"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
