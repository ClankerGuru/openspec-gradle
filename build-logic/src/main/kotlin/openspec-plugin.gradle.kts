// Convention for nested subprojects (plugin/ and tasks/).
// Uses kotlin("jvm") WITHOUT java-library to avoid circular dependency.
plugins {
    kotlin("jvm")
}

group = "zone.clanker"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
