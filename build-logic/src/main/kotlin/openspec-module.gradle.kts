// Convention plugin for all openspec-gradle library submodules.
// Replaces the legacy subprojects {} block in root build.gradle.kts.
plugins {
    `java-library`
    kotlin("jvm")
    `maven-publish`
}

group = "zone.clanker"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Publish submodule JARs to mavenLocal so the plugin's transitive deps resolve in CI.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
