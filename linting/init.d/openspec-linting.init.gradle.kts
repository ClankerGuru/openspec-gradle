/**
 * OpenSpec Linting Init Script
 * 
 * Auto-applies the OpenSpec linting plugin to all Kotlin projects.
 * Place in ~/.gradle/init.d/ for machine-wide enforcement.
 * 
 * Control via system properties:
 *   -Dopenspec.linting.enabled=false  → skip all linting
 *   -Dopenspec.detekt.enabled=false   → skip detekt only
 *   -Dopenspec.ktlint.enabled=false   → skip ktlint only
 */

initscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker.gradle:linting:+")
    }
}

allprojects {
    afterEvaluate {
        val lintingEnabled = System.getProperty("openspec.linting.enabled") != "false"
        
        if (!lintingEnabled) {
            return@afterEvaluate
        }
        
        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        
        if (isKotlinProject) {
            apply<zone.clanker.gradle.linting.OpenSpecLintingPlugin>()
        }
    }
}
