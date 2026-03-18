package zone.clanker.gradle.generators

/**
 * Tool-agnostic command content.
 */
data class CommandContent(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    val body: String
)

/**
 * Tool-agnostic skill content.
 * Mirrors OpenSpec's SkillTemplate with YAML frontmatter fields.
 */
data class SkillContent(
    val dirName: String,
    val description: String,
    val instructions: String,
    val license: String = "MIT",
    val compatibility: String = "Requires Gradle build system.",
    val metadata: Map<String, String> = mapOf("author" to "openspec-gradle", "version" to zone.clanker.gradle.OpenSpecSettingsPlugin.PLUGIN_VERSION)
)

/**
 * Per-tool path and formatting conventions.
 */
interface ToolAdapter {
    val toolId: String
    fun getCommandFilePath(commandId: String): String
    fun formatCommandFile(content: CommandContent): String
    fun getSkillFilePath(skillDirName: String): String
    fun formatSkillFile(content: SkillContent): String
    /** Path to the root agent instructions file (e.g. CLAUDE.md, .github/copilot-instructions.md) */
    fun getInstructionsFilePath(): String
    /** Whether instructions should be appended to an existing file (with markers) vs written fresh */
    val appendInstructions: Boolean get() = false
}

/**
 * Generates skill file content with YAML frontmatter, matching OpenSpec's format.
 * All tools use the same skill format.
 */
private fun formatSkillWithFrontmatter(content: SkillContent, generatedBy: String = "openspec-gradle:${zone.clanker.gradle.OpenSpecSettingsPlugin.PLUGIN_VERSION}"): String = buildString {
    appendLine("---")
    appendLine("name: ${content.dirName}")
    appendLine("description: ${escapeYaml(content.description)}")
    appendLine("license: ${content.license}")
    appendLine("compatibility: ${content.compatibility}")
    appendLine("metadata:")
    for ((key, value) in content.metadata) {
        appendLine("  $key: \"$value\"")
    }
    appendLine("  generatedBy: \"$generatedBy\"")
    appendLine("---")
    appendLine()
    append(content.instructions)
    appendLine()
}

private fun escapeYaml(value: String): String {
    val needsQuoting = Regex("[:\\n\\r#{}\\[\\],&*!|>'\"% @`]|^\\s|\\s$").containsMatchIn(value)
    return if (needsQuoting) {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        "\"$escaped\""
    } else value
}

private fun formatTagsArray(tags: List<String>): String =
    "[${tags.joinToString(", ") { escapeYaml(it) }}]"

private fun formatYamlCommandWithFrontmatter(content: CommandContent): String = buildString {
    appendLine("---")
    appendLine("name: ${escapeYaml(content.name)}")
    appendLine("description: ${escapeYaml(content.description)}")
    appendLine("category: ${escapeYaml(content.category)}")
    appendLine("tags: ${formatTagsArray(content.tags)}")
    appendLine("---")
    appendLine()
    append(content.body)
    appendLine()
}

object ClaudeAdapter : ToolAdapter {
    override val toolId = "claude"
    override fun getCommandFilePath(commandId: String) = ".claude/commands/opsx/$commandId.md"
    override fun formatCommandFile(content: CommandContent) = formatYamlCommandWithFrontmatter(content)
    override fun getSkillFilePath(skillDirName: String) = ".claude/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".claude/CLAUDE.md"
}

object GitHubCopilotAdapter : ToolAdapter {
    override val toolId = "github-copilot"
    override fun getCommandFilePath(commandId: String) = ".github/prompts/opsx-$commandId.prompt.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        appendLine("---")
        appendLine("description: ${escapeYaml(content.description)}")
        appendLine("---")
        appendLine()
        append(content.body)
        appendLine()
    }
    override fun getSkillFilePath(skillDirName: String) = ".github/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".github/instructions/opsx.instructions.md"
}

object CodexAdapter : ToolAdapter {
    override val toolId = "codex"
    // Codex uses .codex/skills/<name>/SKILL.md — skills are invoked via $skill-name
    // Codex doesn't have a separate commands directory — skills ARE the commands
    override fun getCommandFilePath(commandId: String) = ".codex/skills/opsx-$commandId/SKILL.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        // Format commands as skills since Codex unifies them
        val asSkill = SkillContent(
            dirName = "opsx-${content.id}",
            description = content.description,
            instructions = content.body
        )
        append(formatSkillWithFrontmatter(asSkill))
    }
    override fun getSkillFilePath(skillDirName: String) = ".codex/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = "AGENTS.md"
    override val appendInstructions: Boolean get() = true
}

object OpenCodeAdapter : ToolAdapter {
    override val toolId = "opencode"
    // OpenCode reads commands from .opencode/commands/*.md (project-level)
    override fun getCommandFilePath(commandId: String) = ".opencode/commands/opsx-$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        // OpenCode commands are plain markdown with a description comment at the top
        appendLine("<!-- ${content.description} -->")
        appendLine()
        append(content.body)
        appendLine()
    }
    // OpenCode doesn't have a skills directory — it reads opencode.md at project root
    // We put skills in .opencode/skills/ for organization (OpenCode won't auto-load these,
    // but they can be referenced from opencode.md or used as context)
    override fun getSkillFilePath(skillDirName: String) = ".opencode/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = "AGENTS.md"
    override val appendInstructions: Boolean get() = true
}

object CrushAdapter : ToolAdapter {
    override val toolId = "crush"
    override fun getCommandFilePath(commandId: String) = ".crush/commands/opsx/$commandId.md"
    override fun formatCommandFile(content: CommandContent) = formatYamlCommandWithFrontmatter(content)
    override fun getSkillFilePath(skillDirName: String) = ".crush/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = "AGENTS.md"
    override val appendInstructions: Boolean get() = true
}

object ToolAdapterRegistry {
    private val adapters = mapOf(
        "claude" to ClaudeAdapter,
        "github-copilot" to GitHubCopilotAdapter,
        "codex" to CodexAdapter,
        "opencode" to OpenCodeAdapter,
        "crush" to CrushAdapter
    )

    fun get(toolId: String): ToolAdapter? = adapters[toolId]
    fun all(): Collection<ToolAdapter> = adapters.values
    fun supportedTools(): Set<String> = adapters.keys
}
