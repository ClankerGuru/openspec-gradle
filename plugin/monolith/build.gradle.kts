plugins {
    id("openspec-plugin")
}

dependencies {
    api(project(":core"))
    api(project(":monolith-tasks"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
