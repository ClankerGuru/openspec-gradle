plugins {
    id("openspec-plugin")
}

dependencies {
    api(project(":core"))
    api(project(":openspec-tasks"))
    api(project(":generators"))
    api(project(":adapters:claude"))
    api(project(":adapters:copilot"))
    api(project(":adapters:codex"))
    api(project(":adapters:opencode"))
    api(project(":exec"))
    compileOnly(gradleApi())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
