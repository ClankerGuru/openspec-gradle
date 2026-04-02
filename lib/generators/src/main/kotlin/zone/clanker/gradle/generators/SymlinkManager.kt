package zone.clanker.gradle.generators

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates symlinks from per-project agent config directories to the shared ~/.clkx/ directory.
 *
 * For each agent, links the skills directory and instructions file:
 * - Claude: `.claude/skills -> ~/.clkx/skills/claude`, `.claude/CLAUDE.md -> ~/.clkx/instructions/CLAUDE.md`
 * - Copilot: `.github/skills -> ~/.clkx/skills/copilot`, `.github/copilot-instructions.md -> ~/.clkx/instructions/copilot-instructions.md`
 * - Codex: `.agents/skills -> ~/.clkx/skills/codex`, `AGENTS.md -> ~/.clkx/instructions/AGENTS.md`
 * - OpenCode: `.opencode/skills -> ~/.clkx/skills/opencode`
 *
 * If a target path is already a symlink pointing to the right place, it is skipped.
 * If a target path is a real file/directory (team-committed), it is NOT overwritten — the caller
 * should fall back to [MarkerAppender] for instructions, and copy-install for skills.
 */
object SymlinkManager {

    /**
     * Symlink mapping: project-relative path -> clkx-relative path.
     */
    private data class LinkSpec(val projectRelative: String, val clkxRelative: String)

    private val AGENT_LINKS: Map<String, List<LinkSpec>> = mapOf(
        "claude" to listOf(
            LinkSpec(".claude/skills", "skills/claude"),
            LinkSpec(".claude/CLAUDE.md", "instructions/CLAUDE.md"),
        ),
        "github-copilot" to listOf(
            LinkSpec(".github/skills", "skills/copilot"),
            LinkSpec(".github/copilot-instructions.md", "instructions/copilot-instructions.md"),
        ),
        "codex" to listOf(
            LinkSpec(".agents/skills", "skills/codex"),
            LinkSpec("AGENTS.md", "instructions/AGENTS.md"),
        ),
        "opencode" to listOf(
            LinkSpec(".opencode/skills", "skills/opencode"),
        ),
    )

    /**
     * Result of a symlink operation for a single path.
     */
    enum class LinkResult {
        /** Symlink already existed and pointed to the correct target. */
        SKIPPED,
        /** Symlink was created successfully. */
        CREATED,
        /** Target is a real file/directory — not overwritten. Caller should use MarkerAppender. */
        REAL_FILE,
        /** Symlink creation failed; fell back to copying. */
        COPIED,
        /** Symlink creation and copy both failed, or target does not exist. */
        FAILED,
    }

    /**
     * Create symlinks for all configured agents.
     *
     * @param projectDir the project root directory
     * @param agents list of agent IDs (e.g., "claude", "github-copilot", "codex", "opencode")
     * @param clkxDir the shared clkx directory (defaults to ~/.clkx/)
     * @return map of project-relative path to result for each link attempted
     */
    fun createSymlinks(
        projectDir: File,
        agents: List<String>,
        clkxDir: File = ClkxWriter.clkxDir(),
    ): Map<String, LinkResult> {
        val results = mutableMapOf<String, LinkResult>()

        for (agent in agents) {
            val specs = AGENT_LINKS[agent] ?: continue
            for (spec in specs) {
                val linkPath = projectDir.toPath().resolve(spec.projectRelative)
                val targetPath = clkxDir.toPath().resolve(spec.clkxRelative)
                results[spec.projectRelative] = createLink(linkPath, targetPath)
            }
        }

        return results
    }

    /**
     * Create a single symlink from [linkPath] pointing to [targetPath].
     */
    private fun createLink(linkPath: Path, targetPath: Path): LinkResult {
        val linkFile = linkPath.toFile()

        // If already a symlink, check if it points to the right target
        if (Files.isSymbolicLink(linkPath)) {
            val existing = Files.readSymbolicLink(linkPath)
            if (existing == targetPath || linkPath.parent.resolve(existing).normalize() == targetPath.normalize()) {
                return LinkResult.SKIPPED
            }
            // Wrong target — remove and recreate
            Files.delete(linkPath)
        } else if (linkFile.exists()) {
            // Real file or directory — don't overwrite
            return LinkResult.REAL_FILE
        }

        // Ensure parent directory exists
        linkPath.parent?.toFile()?.mkdirs()

        // Attempt to create symlink
        return try {
            Files.createSymbolicLink(linkPath, targetPath)
            LinkResult.CREATED
        } catch (_: Exception) {
            // Fallback: copy the target to the link location (Windows, permissions, etc.)
            try {
                val targetFile = targetPath.toFile()
                if (targetFile.isDirectory) {
                    targetFile.copyRecursively(linkFile, overwrite = true)
                    LinkResult.COPIED
                } else if (targetFile.isFile) {
                    targetFile.copyTo(linkFile, overwrite = true)
                    LinkResult.COPIED
                } else {
                    // Target doesn't exist — nothing to copy
                    LinkResult.FAILED
                }
            } catch (_: Exception) {
                // Both symlink and copy failed
                LinkResult.FAILED
            }
        }
    }
}
