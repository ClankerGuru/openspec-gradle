package zone.clanker.gradle.generators

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates per-skill symlinks from project agent directories to ~/.clkx/.
 *
 * Skills are symlinked individually so project-specific skills can coexist:
 * ```
 * .claude/skills/                          ← REAL directory
 *   srcx-find → ~/.clkx/skills/claude/srcx-find      ← symlink (ours)
 *   opsx-propose → ~/.clkx/skills/claude/opsx-propose ← symlink (ours)
 *   my-custom-skill/SKILL.md                          ← real (project-specific)
 * ```
 *
 * Instructions files are symlinked as whole files (CLAUDE.md, copilot-instructions.md).
 */
object SymlinkManager {

    private data class LinkSpec(val projectRelative: String, val clkxRelative: String)

    /**
     * Agent config: skills parent dir + clkx subdirectory + instruction file links.
     */
    private data class AgentConfig(
        val skillsDir: String,
        val clkxSkillsDir: String,
        val instructionLinks: List<LinkSpec>,
    )

    private val AGENT_CONFIG: Map<String, AgentConfig> = mapOf(
        "claude" to AgentConfig(
            skillsDir = ".claude/skills",
            clkxSkillsDir = "skills/claude",
            instructionLinks = listOf(LinkSpec(".claude/CLAUDE.md", "instructions/CLAUDE.md")),
        ),
        "github-copilot" to AgentConfig(
            skillsDir = ".github/skills",
            clkxSkillsDir = "skills/copilot",
            instructionLinks = listOf(LinkSpec(".github/copilot-instructions.md", "instructions/copilot-instructions.md")),
        ),
        "codex" to AgentConfig(
            skillsDir = ".agents/skills",
            clkxSkillsDir = "skills/codex",
            instructionLinks = listOf(LinkSpec("AGENTS.md", "instructions/AGENTS.md")),
        ),
        "opencode" to AgentConfig(
            skillsDir = ".opencode/skills",
            clkxSkillsDir = "skills/opencode",
            instructionLinks = emptyList(),
        ),
    )

    enum class LinkResult {
        SKIPPED, CREATED, REAL_FILE, COPIED, FAILED,
    }

    /**
     * Create per-skill symlinks for all configured agents.
     * The skills parent directory is created as a REAL directory.
     * Each skill inside is symlinked individually to ~/.clkx/.
     * Instructions files are symlinked as whole files.
     */
    fun createSymlinks(
        projectDir: File,
        agents: List<String>,
        clkxDir: File = ClkxWriter.clkxDir(),
    ): Map<String, LinkResult> {
        val results = mutableMapOf<String, LinkResult>()

        for (agent in agents) {
            val config = AGENT_CONFIG[agent] ?: continue

            // 1. Instructions file symlinks (whole file)
            for (spec in config.instructionLinks) {
                val linkPath = projectDir.toPath().resolve(spec.projectRelative)
                val targetPath = clkxDir.toPath().resolve(spec.clkxRelative)
                results[spec.projectRelative] = createLink(linkPath, targetPath)
            }

            // 2. Skills: create parent as real dir, symlink each skill individually
            val skillsParent = File(projectDir, config.skillsDir)
            val clkxSkillsDir = File(clkxDir, config.clkxSkillsDir)

            if (!clkxSkillsDir.exists()) continue

            // If skills parent is currently a symlink to clkx (old style), remove it first
            val skillsParentPath = skillsParent.toPath()
            if (Files.isSymbolicLink(skillsParentPath)) {
                Files.delete(skillsParentPath)
            }

            // Create real directory
            skillsParent.mkdirs()

            // Symlink each skill dir
            val skillDirs = clkxSkillsDir.listFiles()?.filter { it.isDirectory } ?: continue
            for (skillDir in skillDirs) {
                val linkPath = skillsParent.toPath().resolve(skillDir.name)
                val targetPath = skillDir.toPath()
                val relPath = "${config.skillsDir}/${skillDir.name}"
                results[relPath] = createLink(linkPath, targetPath)
            }
        }

        return results
    }

    fun supportedAgents(): Set<String> = AGENT_CONFIG.keys

    /**
     * Remove our symlinks for the given agents.
     * Only removes symlinks pointing into ~/.clkx/. Real dirs (project-specific) are untouched.
     */
    fun removeSymlinks(
        projectDir: File,
        agents: List<String>,
        clkxDir: File = ClkxWriter.clkxDir(),
    ): Int {
        var removed = 0
        val clkxPath = clkxDir.toPath().normalize()

        for (agent in agents) {
            val config = AGENT_CONFIG[agent] ?: continue

            // Remove instruction symlinks
            for (spec in config.instructionLinks) {
                removed += removeIfClkxSymlink(projectDir.toPath().resolve(spec.projectRelative), clkxPath, projectDir)
            }

            // Remove per-skill symlinks inside the skills parent
            val skillsParent = File(projectDir, config.skillsDir)
            if (skillsParent.exists() && skillsParent.isDirectory) {
                val entries = skillsParent.listFiles() ?: continue
                for (entry in entries) {
                    removed += removeIfClkxSymlink(entry.toPath(), clkxPath, projectDir)
                }
                // Prune skills parent if empty after cleanup
                if (skillsParent.listFiles()?.isEmpty() == true) {
                    skillsParent.delete()
                    AgentCleaner.pruneEmptyParents(skillsParent, projectDir)
                }
            }
        }
        return removed
    }

    private fun removeIfClkxSymlink(linkPath: Path, clkxPath: Path, projectDir: File): Int {
        if (!Files.isSymbolicLink(linkPath)) return 0
        val target = try { Files.readSymbolicLink(linkPath) } catch (_: Exception) { return 0 }
        val resolved = linkPath.parent.resolve(target).normalize()
        if (resolved.startsWith(clkxPath)) {
            Files.delete(linkPath)
            AgentCleaner.pruneEmptyParents(linkPath.parent?.toFile(), projectDir)
            return 1
        }
        return 0
    }

    private fun createLink(linkPath: Path, targetPath: Path): LinkResult {
        val linkFile = linkPath.toFile()

        if (Files.isSymbolicLink(linkPath)) {
            val existing = Files.readSymbolicLink(linkPath)
            if (existing == targetPath || linkPath.parent.resolve(existing).normalize() == targetPath.normalize()) {
                return LinkResult.SKIPPED
            }
            Files.delete(linkPath)
        } else if (linkFile.exists()) {
            return LinkResult.REAL_FILE
        }

        linkPath.parent?.toFile()?.mkdirs()

        return try {
            Files.createSymbolicLink(linkPath, targetPath)
            LinkResult.CREATED
        } catch (_: Exception) {
            try {
                val targetFile = targetPath.toFile()
                if (targetFile.isDirectory) {
                    targetFile.copyRecursively(linkFile, overwrite = true)
                    LinkResult.COPIED
                } else if (targetFile.isFile) {
                    targetFile.copyTo(linkFile, overwrite = true)
                    LinkResult.COPIED
                } else {
                    LinkResult.FAILED
                }
            } catch (_: Exception) {
                LinkResult.FAILED
            }
        }
    }
}
