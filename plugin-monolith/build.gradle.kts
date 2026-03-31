plugins {
    id("openspec-module")
    id("openspec-publish")
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    api(project(":core"))
    api(project(":monolith-tasks"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("monolith") {
            id = "zone.clanker.monolith"
            implementationClass = "zone.clanker.monolith.MonolithPlugin"
        }
    }
}
