package zone.clanker.gradle.generators

import zone.clanker.gradle.core.VersionInfo

/**
 * Tool-agnostic skill content.
 * Mirrors OpenSpec's SkillTemplate with YAML frontmatter fields.
 */
data class SkillContent(
    val dirName: String,
    val description: String,
    val instructions: String,
    /** Version string for generated-by comments (defaults to plugin version) */
    val version: String = VersionInfo.PLUGIN_VERSION,
    /** Claude Code: hint shown in autocomplete (e.g., "[symbol-name]") */
    val argumentHint: String? = null,
    /** Claude Code: glob patterns for auto-activation (e.g., "*.kt,*.kts") */
    val paths: String? = null,
    /** Claude Code: set false to hide from user menu but keep visible to model */
    val userInvocable: Boolean? = null
)

/**
 * Per-tool path and formatting conventions.
 */
interface ToolAdapter {
    val toolId: String
    fun getSkillFilePath(skillDirName: String): String
    fun formatSkillFile(content: SkillContent): String
    /** Path to the root agent instructions file (e.g. .claude/CLAUDE.md, .github/copilot-instructions.md, AGENTS.md) */
    fun getInstructionsFilePath(): String
    /** Whether instructions should be appended to an existing file (with markers) vs written fresh */
    val appendInstructions: Boolean get() = false
    /**
     * Short directory name used under ~/.clkx/skills/ for global mode.
     * Defaults to [toolId] but can be overridden (e.g., "github-copilot" -> "copilot").
     */
    val globalDirName: String get() = toolId
}

/**
 * Generates skill file content with minimal YAML frontmatter (name + description only).
 * Used by Copilot, Codex, and OpenCode adapters.
 */
fun formatSkillWithFrontmatter(content: SkillContent): String {
    return """
        |---
        |name: ${escapeYaml(content.dirName)}
        |description: ${escapeYaml(content.description)}
        |---
        |
        |${content.instructions}
    """.trimMargin() + "\n"
}

/**
 * Claude Code-specific frontmatter — only fields Claude Code recognizes.
 * Omits license, compatibility, and metadata which are not part of the Claude Code SKILL.md spec.
 */
fun formatSkillForClaude(content: SkillContent): String {
    val version = content.version
    val sb = StringBuilder()
    sb.appendLine("---")
    sb.appendLine("name: ${escapeYaml(content.dirName)}")
    sb.appendLine("description: ${escapeYaml(content.description)}")
    content.argumentHint?.let { sb.appendLine("argument-hint: ${escapeYaml(it)}") }
    content.paths?.let { sb.appendLine("paths: ${escapeYaml(it)}") }
    content.userInvocable?.let { sb.appendLine("user-invocable: $it") }
    sb.appendLine("---")
    sb.appendLine()
    sb.appendLine("<!-- openspec-gradle:$version -->")
    sb.appendLine()
    sb.append(content.instructions)
    sb.appendLine()
    return sb.toString()
}

private val YAML_NEEDS_QUOTING = Regex("[:\\n\\r#{}\\[\\],&*!|>'\"% @`]|^\\s|\\s$")

fun escapeYaml(value: String): String {
    if (value.isEmpty()) return "\"\""
    val needsQuoting = YAML_NEEDS_QUOTING.containsMatchIn(value)
    return if (needsQuoting) {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        "\"$escaped\""
    } else value
}

object ToolAdapterRegistry {
    private val adapters = mutableMapOf<String, ToolAdapter>()

    fun register(adapter: ToolAdapter) {
        adapters[adapter.toolId] = adapter
    }

    fun get(toolId: String): ToolAdapter? = adapters[toolId]
    fun all(): Collection<ToolAdapter> = adapters.values
    fun supportedTools(): Set<String> = adapters.keys
}
