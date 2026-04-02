package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import zone.clanker.gradle.generators.AgentCleaner
import zone.clanker.gradle.generators.ClkxWriter
import zone.clanker.gradle.generators.MarkerAppender
import zone.clanker.gradle.generators.ToolAdapterRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

@org.gradle.api.tasks.UntrackedTask(because = "Removes files from project directory")
abstract class CleanTask : DefaultTask() {

    @get:Input
    abstract val tools: ListProperty<String>

    init {
        group = OPSX_GROUP
        description = "[tool] Remove all generated OpenSpec files. " +
            "Use when: Switching agents, uninstalling, or cleaning up."
    }

    @TaskAction
    fun clean() {
        var count = 0

        val seenInstrPaths = mutableSetOf<String>()
        // Clean ALL supported tools, not just configured ones
        for (toolId in ToolAdapterRegistry.supportedTools()) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue

            // Deduplicate shared instructions paths (e.g., AGENTS.md used by codex + opencode)
            val instrPath = adapter.getInstructionsFilePath()
            if (instrPath in seenInstrPaths) {
                // Only clean skills for this adapter, not the shared instructions file
                val skillsDir = AgentCleaner.resolveSkillsDir(project.projectDir, adapter)
                if (skillsDir != null && skillsDir.exists() && skillsDir.isDirectory) {
                    skillsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("opsx-") }?.forEach {
                        it.deleteRecursively()
                        count++
                    }
                    AgentCleaner.pruneEmptyParents(skillsDir, project.projectDir)
                }
            } else {
                seenInstrPaths.add(instrPath)
                count += AgentCleaner.cleanAgent(project.projectDir, adapter)
            }
        }

        // Remove .opsx/ directory (context.md etc.)
        val opsxDir = File(project.projectDir, ".opsx")
        if (opsxDir.exists()) {
            opsxDir.deleteRecursively()
            logger.lifecycle("OpenSpec: Removed .opsx/ directory")
        }

        // NOTE: opsx/changes/ is intentionally preserved — proposals are committed work.

        // Clean generated files from included builds
        val includedCount = cleanIncludedBuilds()
        if (includedCount > 0) {
            logger.lifecycle("OpenSpec: Cleaned $includedCount items from included builds")
        }

        logger.lifecycle("OpenSpec: Cleaned $count generated files")
    }

    /**
     * Clean OPSX-generated files from each included build's project directory.
     * Only removes:
     * - Symlinks pointing to ~/.clkx/ (deletes the symlink, not the target)
     * - Files with OPSX markers (uses MarkerAppender.remove() instead of deleting)
     * - .opsx/ directories
     *
     * Never touches files that aren't ours.
     */
    private fun cleanIncludedBuilds(): Int {
        val includedBuilds = project.gradle.includedBuilds
        if (includedBuilds.isEmpty()) return 0

        val clkxDir = ClkxWriter.clkxDir().toPath().toRealPath()
        var count = 0

        for (build in includedBuilds) {
            val buildDir = build.projectDir
            logger.lifecycle("OpenSpec: Cleaning included build '${build.name}' at ${buildDir.path}")

            // 1. Remove symlinks pointing to ~/.clkx/
            count += removeClkxSymlinks(buildDir, clkxDir)

            // 2. Remove OPSX marker sections from instruction files (don't delete the file)
            count += removeMarkerSections(buildDir)

            // 3. Remove .opsx/ directory
            val opsxDir = File(buildDir, ".opsx")
            if (opsxDir.exists()) {
                opsxDir.deleteRecursively()
                logger.lifecycle("OpenSpec: Removed .opsx/ from included build '${build.name}'")
                count++
            }
        }

        return count
    }

    /**
     * Find and remove symlinks under [dir] that point into ~/.clkx/.
     * Only deletes the symlink itself, never the target.
     */
    private fun removeClkxSymlinks(dir: File, clkxPath: java.nio.file.Path): Int {
        var count = 0
        // Check known agent config directories for symlinks
        val candidates = listOf(
            ".claude/skills", ".claude/CLAUDE.md",
            ".github/skills", ".github/copilot-instructions.md",
            ".agents/skills", "AGENTS.md",
            ".opencode/skills",
        )
        for (candidate in candidates) {
            val path = dir.toPath().resolve(candidate)
            if (Files.isSymbolicLink(path)) {
                try {
                    val target = Files.readSymbolicLink(path)
                    val resolvedTarget = path.parent.resolve(target).normalize()
                    if (resolvedTarget.startsWith(clkxPath)) {
                        Files.delete(path)
                        count++
                    }
                } catch (_: Exception) {
                    // Skip if we can't read/delete the symlink
                }
            }
        }
        // Prune empty parent directories left behind
        for (candidate in candidates) {
            val path = File(dir, candidate)
            AgentCleaner.pruneEmptyParents(path.parentFile, dir)
        }
        return count
    }

    /**
     * Find instruction files with OPSX markers in [dir] and remove the marker sections.
     * Does not delete the files — only strips the OPSX-generated content.
     */
    private fun removeMarkerSections(dir: File): Int {
        var count = 0
        // Check known instruction file locations
        val instrPaths = listOf(
            ".claude/CLAUDE.md",
            ".github/copilot-instructions.md",
            "AGENTS.md",
        )
        for (instrPath in instrPaths) {
            val file = File(dir, instrPath)
            if (file.exists() && !Files.isSymbolicLink(file.toPath()) && MarkerAppender.hasMarkers(file)) {
                MarkerAppender.remove(file)
                count++
            }
        }
        return count
    }
}
