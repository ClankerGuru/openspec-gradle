package zone.clanker.gradle.generators

import zone.clanker.gradle.core.VersionInfo

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
    val metadata: Map<String, String> = mapOf("author" to "openspec-gradle", "version" to VersionInfo.PLUGIN_VERSION)
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
    /** Path to the root agent instructions file (e.g. .claude/CLAUDE.md, .github/copilot-instructions.md, AGENTS.md) */
    fun getInstructionsFilePath(): String
    /** Whether instructions should be appended to an existing file (with markers) vs written fresh */
    val appendInstructions: Boolean get() = false
}

/**
 * Generates skill file content with YAML frontmatter, matching OpenSpec's format.
 * All tools use the same skill format.
 */
fun formatSkillWithFrontmatter(content: SkillContent, generatedBy: String = "openspec-gradle:${VersionInfo.PLUGIN_VERSION}"): String = buildString {
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

fun escapeYaml(value: String): String {
    val needsQuoting = Regex("[:\\n\\r#{}\\[\\],&*!|>'\"% @`]|^\\s|\\s$").containsMatchIn(value)
    return if (needsQuoting) {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        "\"$escaped\""
    } else value
}

fun formatTagsArray(tags: List<String>): String =
    "[${tags.joinToString(", ") { escapeYaml(it) }}]"

fun formatYamlCommandWithFrontmatter(content: CommandContent): String = buildString {
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

object ToolAdapterRegistry {
    private val adapters = mutableMapOf<String, ToolAdapter>()

    fun register(adapter: ToolAdapter) {
        adapters[adapter.toolId] = adapter
    }

    fun get(toolId: String): ToolAdapter? = adapters[toolId]
    fun all(): Collection<ToolAdapter> = adapters.values
    fun supportedTools(): Set<String> = adapters.keys
}
