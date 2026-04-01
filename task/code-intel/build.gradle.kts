plugins {
    id("openspec-plugin")
    id("openspec-publish")
}

dependencies {
    implementation(project(":lib:core"))
    implementation(project(":lib:psi"))
    implementation(project(":lib:arch"))
    implementation(project(":lib:quality"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
