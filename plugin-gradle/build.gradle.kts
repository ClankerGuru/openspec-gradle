plugins {
    id("openspec-module")
    id("openspec-publish")
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    api(project(":core"))
    api(project(":gradle-tasks"))
    api(project(":psi"))
    api(project(":quality"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("gradle") {
            id = "zone.clanker.gradle"
            implementationClass = "zone.clanker.gradle.GradlePlugin"
        }
    }
}
