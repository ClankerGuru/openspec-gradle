plugins {
    id("openspec-module")
    id("openspec-publish")
    `kotlin-dsl`
    `java-gradle-plugin`
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
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("openspec") {
            id = "zone.clanker.openspec"
            implementationClass = "zone.clanker.openspec.OpenSpecPlugin"
        }
    }
}
