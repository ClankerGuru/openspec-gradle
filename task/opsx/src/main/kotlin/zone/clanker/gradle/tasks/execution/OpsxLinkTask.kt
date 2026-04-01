package zone.clanker.gradle.tasks.execution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

/**
 * Links tool config directories from ~/.config/opsx to the current project.
 * Creates symlinks for .claude, .github, .agents, and .opencode directories.
 */
@org.gradle.api.tasks.UntrackedTask(because = "Creates symlinks to user config")
abstract class OpsxLinkTask : DefaultTask() {

    companion object {
        // Tool directories to symlink
        val TOOL_DIRS = listOf(".claude", ".github", ".agents", ".opencode")
    }

    init {
        group = "opsx"
        description = "[tool] Link tool configs from ~/.config/opsx (skills & commands)"
    }

    @TaskAction
    fun link() {
        val home = System.getProperty("user.home")
        val opsxDir = File(home, ".config/opsx")
        val projectDir = project.projectDir

        if (!opsxDir.exists()) {
            logger.lifecycle("Creating ~/.config/opsx/")
            for (toolDir in TOOL_DIRS) {
                File(opsxDir, toolDir).mkdirs()
            }
        }

        var created = 0
        var skipped = 0

        for (toolDir in TOOL_DIRS) {
            val source = File(opsxDir, toolDir)
            val target = File(projectDir, toolDir)
            
            // Create source dir if missing
            if (!source.exists()) {
                source.mkdirs()
            }
            
            if (syncDir(source, target)) created++ else skipped++
        }

        logger.lifecycle("")
        logger.lifecycle("opsx-link: $created linked, $skipped skipped")
    }

    private fun syncDir(source: File, target: File): Boolean {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()

        // Target already exists
        if (target.exists() || Files.isSymbolicLink(targetPath)) {
            if (Files.isSymbolicLink(targetPath)) {
                val linkTarget = Files.readSymbolicLink(targetPath)
                if (linkTarget == sourcePath || targetPath.resolveSibling(linkTarget).normalize() == sourcePath.normalize()) {
                    logger.lifecycle("  ✓  ${target.name}/ (already linked)")
                } else {
                    logger.lifecycle("  ⚠️  ${target.name}/ (symlink to different target)")
                }
            } else {
                logger.lifecycle("  ⚠️  ${target.name}/ (exists, not a symlink)")
            }
            return false
        }

        // Create symlink
        return try {
            Files.createSymbolicLink(targetPath, sourcePath)
            logger.lifecycle("  🔗 ${target.name}/ -> $source")
            true
        } catch (e: Exception) {
            handleSymlinkError(e, target.name)
            false
        }
    }

    private fun handleSymlinkError(e: Exception, name: String) {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        
        if (isWindows && e.message?.contains("privilege") == true) {
            logger.error("")
            logger.error("❌ Cannot create symlinks - Windows Developer Mode required")
            logger.error("")
            logger.error("   To fix (one-time):")
            logger.error("   1. Run: start ms-settings:developers")
            logger.error("   2. Enable 'Developer Mode'")
            logger.error("   3. Run this task again")
            logger.error("")
        } else {
            logger.error("  ❌ $name/ (failed: ${e.message})")
        }
    }
}
