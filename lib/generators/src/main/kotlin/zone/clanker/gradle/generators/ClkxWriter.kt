package zone.clanker.gradle.generators

import java.io.File

/**
 * Writes core skills and instructions to ~/.clkx/ for machine-level sharing.
 *
 * Each agent gets its own subdirectory with skills formatted for that agent:
 * ```
 * ~/.clkx/
 *   skills/
 *     claude/srcx-find/SKILL.md       (Claude frontmatter)
 *     copilot/srcx-find/SKILL.md      (YAML frontmatter)
 *     codex/srcx-find/SKILL.md        (YAML frontmatter)
 *     opencode/srcx-find/SKILL.md     (YAML frontmatter)
 *   instructions/
 *     CLAUDE.md
 *     copilot-instructions.md
 *     AGENTS.md
 * ```
 *
 * On each call, the skills directory is wiped and regenerated.
 * Only core skills (from TemplateRegistry) are written — no task-code skills.
 */
object ClkxWriter {

    private val CLKX_DIR_NAME = ".clkx"

    fun clkxDir(): File = File(System.getProperty("user.home"), CLKX_DIR_NAME)

    /**
     * Write all core skills for all registered tool adapters.
     * Wipes and regenerates ~/.clkx/skills/ on each call.
     *
     * @param tools list of tool IDs to generate for (e.g., ["claude", "github-copilot", "codex", "opencode"])
     * @param targetDir override for testing (defaults to ~/.clkx/)
     * @return number of skill files written
     */
    fun writeSkills(
        tools: List<String>,
        targetDir: File = clkxDir(),
    ): Int {
        val skillsDir = File(targetDir, "skills")

        // Wipe and recreate
        if (skillsDir.exists()) skillsDir.deleteRecursively()
        skillsDir.mkdirs()

        val skills = TemplateRegistry.getSkillTemplates()
        var count = 0

        for (toolId in tools) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            val agentDir = File(skillsDir, adapter.globalDirName)

            for (skill in skills) {
                // Get the filename part from the adapter's path pattern
                // e.g., ".claude/skills/srcx-find/SKILL.md" → "srcx-find/SKILL.md"
                val fullPath = adapter.getSkillFilePath(skill.dirName)
                val fileName = fullPath.substringAfterLast("skills/")

                val outFile = File(agentDir, fileName)
                outFile.parentFile.mkdirs()
                outFile.writeText(adapter.formatSkillFile(skill))
                count++
            }
        }

        return count
    }

    /**
     * Write instruction files for all registered tool adapters.
     * Wipes and regenerates ~/.clkx/instructions/ on each call.
     *
     * @param tools list of tool IDs
     * @param instructionsContent the generated instructions markdown content
     * @param targetDir override for testing
     */
    fun writeInstructions(
        tools: List<String>,
        instructionsContent: String,
        targetDir: File = clkxDir(),
    ) {
        val instrDir = File(targetDir, "instructions")
        if (instrDir.exists()) instrDir.deleteRecursively()
        instrDir.mkdirs()

        for (toolId in tools) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            // Get the instruction file name from the adapter
            // e.g., ".claude/CLAUDE.md" → "CLAUDE.md"
            val instrPath = adapter.getInstructionsFilePath()
            val fileName = instrPath.substringAfterLast("/")

            // Codex and OpenCode both use AGENTS.md — deduplicate
            val outFile = File(instrDir, fileName)
            if (!outFile.exists()) {
                outFile.writeText(instructionsContent)
            }
        }
    }

    /**
     * Full write: skills + instructions.
     * Called by `opsx-sync --global` and `install.sh`.
     */
    fun writeAll(
        tools: List<String>,
        instructionsContent: String,
        targetDir: File = clkxDir(),
    ): Int {
        targetDir.mkdirs()
        writeInstructions(tools, instructionsContent, targetDir)
        return writeSkills(tools, targetDir)
    }
}
