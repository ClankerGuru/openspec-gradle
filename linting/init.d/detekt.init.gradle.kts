/**
 * Detekt Init Script for OpenSpec Gradle Distribution
 * 
 * Auto-applies detekt to all Kotlin projects.
 * Place in ~/.gradle/init.d/ or your custom Gradle distribution's init.d/
 * 
 * To disable for a specific project, add to gradle.properties:
 *   openspec.detekt.enabled=false
 */

initscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    }
}

allprojects {
    afterEvaluate {
        val detektEnabled = findProperty("openspec.detekt.enabled")?.toString() != "false"
        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") || 
            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val alreadyHasDetekt = plugins.hasPlugin("io.gitlab.arturbosch.detekt")
        
        if (alreadyHasDetekt) {
            println("🔍 [OpenSpec] detekt already configured for ${project.name} — skipping")
            return@afterEvaluate
        }
        
        if (detektEnabled && isKotlinProject) {
            apply<io.gitlab.arturbosch.detekt.DetektPlugin>()
            
            extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
                buildUponDefaultConfig = true
                
                // Look for config in project first, then fall back to default
                val projectConfig = file("$rootDir/config/detekt.yml")
                if (projectConfig.exists()) {
                    config.setFrom(projectConfig)
                }
            }
            
            // Make build depend on detekt
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
            
            println("🔍 [OpenSpec] detekt enabled for ${project.name}")
        }
    }
}
