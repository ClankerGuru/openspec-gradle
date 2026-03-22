/**
 * ktlint Init Script for OpenSpec Gradle Distribution
 * 
 * Auto-applies ktlint to all Kotlin projects.
 * Place in ~/.gradle/init.d/ or your custom Gradle distribution's init.d/
 * 
 * To disable for a specific project, add to gradle.properties:
 *   openspec.ktlint.enabled=false
 */

initscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    }
}

allprojects {
    afterEvaluate {
        val ktlintEnabled = findProperty("openspec.ktlint.enabled")?.toString() != "false"
        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") || 
            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val alreadyHasKtlint = plugins.hasPlugin("org.jlleitschuh.gradle.ktlint")
        
        if (alreadyHasKtlint) {
            println("🧹 [OpenSpec] ktlint already configured for ${project.name} — skipping")
            return@afterEvaluate
        }
        
        if (ktlintEnabled && isKotlinProject) {
            apply<org.jlleitschuh.gradle.ktlint.KtlintPlugin>()
            
            extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
                version.set("1.5.0")
                android.set(plugins.hasPlugin("org.jetbrains.kotlin.android"))
                outputToConsole.set(true)
                ignoreFailures.set(false)
            }
            
            // Make check depend on ktlintCheck
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("ktlintCheck")
            }
            
            println("🧹 [OpenSpec] ktlint enabled for ${project.name}")
        }
    }
}
