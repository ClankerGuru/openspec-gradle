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

// Disable strict validation — plugin task classes extend Exec which handles caching.
tasks.withType<ValidatePlugins>().configureEach {
    enableStricterValidation.set(false)
    failOnWarning.set(false)
}
