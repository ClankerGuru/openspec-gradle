// Convention for nested plugin modules.
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
