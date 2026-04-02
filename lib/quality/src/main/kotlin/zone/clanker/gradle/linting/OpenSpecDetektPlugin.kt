package zone.clanker.gradle.linting

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Auto-applies detekt if not already configured.
 *
 * System properties:
 *   -Dopenspec.detekt.enabled=false  → skip detekt entirely
 *   -Dopenspec.detekt.config=/path   → custom config file
 *
 * Project properties (gradle.properties):
 *   openspec.detekt.enabled=false
 */
class OpenSpecDetektPlugin : Plugin<Project> {

    companion object {
        private const val DETEKT_PLUGIN_ID = "io.gitlab.arturbosch.detekt"
        private const val ENABLED_PROP = "zone.clanker.quality.detekt"
        private const val LEGACY_ENABLED_PROP = "openspec.detekt.enabled"
        private const val CONFIG_PROP = "zone.clanker.quality.detekt.config"
        private const val LEGACY_CONFIG_PROP = "openspec.detekt.config"
    }

    override fun apply(project: Project) {
        if (!isEnabled(project)) return

        project.afterEvaluate {
            if (project.plugins.hasPlugin(DETEKT_PLUGIN_ID)) {
                project.logger.lifecycle("🔍 [OpenSpec] detekt already configured for ${project.name} — skipping")
                return@afterEvaluate
            }

            if (!isKotlinProject(project)) {
                return@afterEvaluate
            }

            applyDetekt(project)
        }
    }

    private fun isEnabled(project: Project): Boolean {
        System.getProperty(ENABLED_PROP)?.let { return it.lowercase() != "false" }
        project.findProperty(ENABLED_PROP)?.let { return it.toString().lowercase() != "false" }
        System.getProperty(LEGACY_ENABLED_PROP)?.let { return it.lowercase() != "false" }
        project.findProperty(LEGACY_ENABLED_PROP)?.let { return it.toString().lowercase() != "false" }
        return true
    }

    private fun isKotlinProject(project: Project): Boolean {
        return project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
               project.plugins.hasPlugin("org.jetbrains.kotlin.android") ||
               project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
    }

    private fun applyDetekt(project: Project) {
        project.plugins.apply(DETEKT_PLUGIN_ID)

        val detektExtension = project.extensions.findByName("detekt")
        if (detektExtension != null) {
            // Configure via reflection to avoid compile-time dependency
            detektExtension.javaClass.getMethod("setBuildUponDefaultConfig", Boolean::class.java)
                .invoke(detektExtension, true)

            // Look for config file
            val configPath = System.getProperty(CONFIG_PROP)
                ?: project.findProperty(CONFIG_PROP)?.toString()
                ?: System.getProperty(LEGACY_CONFIG_PROP)
                ?: project.findProperty(LEGACY_CONFIG_PROP)?.toString()

            val configFile = when {
                configPath != null -> File(configPath)
                else -> project.file("${project.rootDir}/config/detekt.yml")
            }

            if (configFile.exists()) {
                val configProperty = detektExtension.javaClass.getMethod("getConfig").invoke(detektExtension)
                configProperty.javaClass.getMethod("setFrom", Array<Any>::class.java).invoke(configProperty, arrayOf<Any>(configFile))
            }
        }

        // Hook into check and build tasks
        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn("detekt")
        }
        project.tasks.matching { it.name == "build" }.configureEach {
            dependsOn("detekt")
        }

        project.logger.lifecycle("🔍 [OpenSpec] detekt enabled for ${project.name}")
    }
}
