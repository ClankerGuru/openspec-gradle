package zone.clanker.gradle.linting

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Auto-applies ktlint if not already configured.
 *
 * System properties:
 *   -Dopenspec.ktlint.enabled=false  → skip ktlint entirely
 *   -Dopenspec.ktlint.version=1.5.0  → custom ktlint version
 *
 * Project properties (gradle.properties):
 *   openspec.ktlint.enabled=false
 */
class OpenSpecKtlintPlugin : Plugin<Project> {

    companion object {
        private const val KTLINT_PLUGIN_ID = "org.jlleitschuh.gradle.ktlint"
        private const val ENABLED_PROP = "openspec.ktlint.enabled"
        private const val VERSION_PROP = "openspec.ktlint.version"
        private const val DEFAULT_VERSION = "1.5.0"
    }

    override fun apply(project: Project) {
        val enabled = isEnabled(project)

        if (!enabled) {
            project.logger.lifecycle("🧹 [OpenSpec] ktlint disabled via system property — skipping")
            return
        }

        project.afterEvaluate {
            if (project.plugins.hasPlugin(KTLINT_PLUGIN_ID)) {
                project.logger.lifecycle("🧹 [OpenSpec] ktlint already configured for ${project.name} — skipping")
                return@afterEvaluate
            }

            if (!isKotlinProject(project)) {
                return@afterEvaluate
            }

            applyKtlint(project)
        }
    }

    private fun isEnabled(project: Project): Boolean {
        // System property takes precedence
        System.getProperty(ENABLED_PROP)?.let {
            return it.lowercase() != "false"
        }
        // Fall back to project property
        project.findProperty(ENABLED_PROP)?.let {
            return it.toString().lowercase() != "false"
        }
        return true
    }

    private fun isKotlinProject(project: Project): Boolean {
        return project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
               project.plugins.hasPlugin("org.jetbrains.kotlin.android") ||
               project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
    }

    private fun applyKtlint(project: Project) {
        project.plugins.apply(KTLINT_PLUGIN_ID)

        val ktlintExtension = project.extensions.findByName("ktlint")
        if (ktlintExtension != null) {
            val version = System.getProperty(VERSION_PROP)
                ?: project.findProperty(VERSION_PROP)?.toString()
                ?: DEFAULT_VERSION

            // Configure via reflection to avoid compile-time dependency
            val versionProperty = ktlintExtension.javaClass.getMethod("getVersion").invoke(ktlintExtension)
            versionProperty.javaClass.getMethod("set", Any::class.java).invoke(versionProperty, version)

            val androidProperty = ktlintExtension.javaClass.getMethod("getAndroid").invoke(ktlintExtension)
            val isAndroid = project.plugins.hasPlugin("org.jetbrains.kotlin.android")
            androidProperty.javaClass.getMethod("set", Any::class.java).invoke(androidProperty, isAndroid)

            val consoleProperty = ktlintExtension.javaClass.getMethod("getOutputToConsole").invoke(ktlintExtension)
            consoleProperty.javaClass.getMethod("set", Any::class.java).invoke(consoleProperty, true)
        }

        // Hook into check and build tasks
        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn("ktlintCheck")
        }
        project.tasks.matching { it.name == "build" }.configureEach {
            dependsOn("ktlintCheck")
        }

        project.logger.lifecycle("🧹 [OpenSpec] ktlint enabled for ${project.name}")
    }
}
