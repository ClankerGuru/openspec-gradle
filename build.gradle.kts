plugins {
    kotlin("jvm") version embeddedKotlinVersion apply false
    alias(libs.plugins.plugin.publish) apply false
    alias(libs.plugins.vanniktech) apply false
}

version = providers.gradleProperty("overrideVersion").orElse(
    providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().removePrefix("v") }
).getOrElse("0.0.0-LOCAL")

// Propagate version to subprojects
subprojects {
    version = rootProject.version
}
