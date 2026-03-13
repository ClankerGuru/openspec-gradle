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
    val metadata: Map<String, String> = mapOf("author" to "openspec-gradle", "version" to "1.0")
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
}

/**
 * Generates skill file content with YAML frontmatter, matching OpenSpec's format.
 * All tools use the same skill format.
 */
private fun formatSkillWithFrontmatter(content: SkillContent, generatedBy: String = "openspec-gradle:0.1.0"): String = buildString {
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

object ClaudeAdapter : ToolAdapter {
    override val toolId = "claude"
    override fun getCommandFilePath(commandId: String) = ".claude/commands/opsx/$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
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
    override fun getSkillFilePath(skillDirName: String) = ".claude/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
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
}

object CursorAdapter : ToolAdapter {
    override val toolId = "cursor"
    override fun getCommandFilePath(commandId: String) = ".cursor/commands/opsx-$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        appendLine("---")
        appendLine("name: /opsx-${content.id}")
        appendLine("id: opsx-${content.id}")
        appendLine("category: ${escapeYaml(content.category)}")
        appendLine("description: ${escapeYaml(content.description)}")
        appendLine("---")
        appendLine()
        append(content.body)
        appendLine()
    }
    override fun getSkillFilePath(skillDirName: String) = ".cursor/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
}

object CodexAdapter : ToolAdapter {
    override val toolId = "codex"
    // Codex prompts: .codex/prompts/opsx-<id>.md (project-local mirror of ~/.codex/prompts/)
    override fun getCommandFilePath(commandId: String) = ".codex/prompts/opsx-$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        appendLine("---")
        appendLine("description: ${escapeYaml(content.description)}")
        appendLine("argument-hint: command arguments")
        appendLine("---")
        appendLine()
        append(content.body)
        appendLine()
    }
    override fun getSkillFilePath(skillDirName: String) = ".codex/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
}

object OpenCodeAdapter : ToolAdapter {
    override val toolId = "opencode"
    // OpenCode commands: .opencode/commands/opsx-<id>.md with description frontmatter
    override fun getCommandFilePath(commandId: String) = ".opencode/commands/opsx-$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        appendLine("---")
        appendLine("description: ${escapeYaml(content.description)}")
        appendLine("---")
        appendLine()
        append(content.body)
        appendLine()
    }
    // OpenCode has no skills directory — put in .opencode/skills/ for organization
    override fun getSkillFilePath(skillDirName: String) = ".opencode/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
}

object CrushAdapter : ToolAdapter {
    override val toolId = "crush"
    // Crush commands: .crush/commands/opsx/<id>.md with full frontmatter
    override fun getCommandFilePath(commandId: String) = ".crush/commands/opsx/$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
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
    override fun getSkillFilePath(skillDirName: String) = ".crush/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
}

object ToolAdapterRegistry {
    private val adapters = mapOf(
        "claude" to ClaudeAdapter,
        "github-copilot" to GitHubCopilotAdapter,
        "cursor" to CursorAdapter,
        "codex" to CodexAdapter,
        "opencode" to OpenCodeAdapter,
        "crush" to CrushAdapter
    )

    fun get(toolId: String): ToolAdapter? = adapters[toolId]
    fun all(): Collection<ToolAdapter> = adapters.values
    fun supportedTools(): Set<String> = adapters.keys
}
