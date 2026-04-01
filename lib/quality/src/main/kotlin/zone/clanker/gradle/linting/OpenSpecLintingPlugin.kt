package zone.clanker.gradle.linting

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convenience plugin that applies both detekt and ktlint.
 *
 * Disable per-project via gradle.properties:
 *   zone.clanker.quality.enabled=false   → skip both
 *   zone.clanker.quality.detekt=false    → skip detekt only
 *   zone.clanker.quality.ktlint=false    → skip ktlint only
 *
 * Global override via system property (-D):
 *   -Dzone.clanker.quality.enabled=false
 */
class OpenSpecLintingPlugin : Plugin<Project> {

    companion object {
        private const val ENABLED_PROP = "zone.clanker.quality.enabled"
        private const val LEGACY_PROP = "openspec.linting.enabled"
    }

    override fun apply(project: Project) {
        if (!isEnabled(project)) return

        project.plugins.apply(OpenSpecDetektPlugin::class.java)
        project.plugins.apply(OpenSpecKtlintPlugin::class.java)
    }

    private fun isEnabled(project: Project): Boolean {
        // New property (system or project)
        System.getProperty(ENABLED_PROP)?.let { return it.lowercase() != "false" }
        project.findProperty(ENABLED_PROP)?.let { return it.toString().lowercase() != "false" }
        // Legacy fallback
        System.getProperty(LEGACY_PROP)?.let { return it.lowercase() != "false" }
        project.findProperty(LEGACY_PROP)?.let { return it.toString().lowercase() != "false" }
        return true
    }
}
