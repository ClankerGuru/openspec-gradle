package zone.clanker.gradle.linting

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convenience plugin that applies both detekt and ktlint.
 * 
 * Usage:
 *   plugins {
 *       id("zone.clanker.gradle.linting")
 *   }
 * 
 * System properties:
 *   -Dopenspec.detekt.enabled=false  → skip detekt
 *   -Dopenspec.ktlint.enabled=false  → skip ktlint
 *   -Dopenspec.linting.enabled=false → skip both
 */
class OpenSpecLintingPlugin : Plugin<Project> {
    
    companion object {
        private const val ENABLED_PROP = "openspec.linting.enabled"
    }
    
    override fun apply(project: Project) {
        val enabled = isEnabled(project)
        
        if (!enabled) {
            project.logger.lifecycle("🔧 [OpenSpec] linting disabled via system property — skipping")
            return
        }
        
        project.plugins.apply(OpenSpecDetektPlugin::class.java)
        project.plugins.apply(OpenSpecKtlintPlugin::class.java)
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
}
