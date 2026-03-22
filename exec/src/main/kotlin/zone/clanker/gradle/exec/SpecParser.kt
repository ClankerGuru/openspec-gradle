package zone.clanker.gradle.exec

import java.io.File

/**
 * Parses task spec files for opsx-exec.
 *
 * Format:
 * ```
 * # Task: Title
 *
 * agent: copilot
 * max-retries: 3
 * verify: true
 *
 * ## Prompt
 *
 * The actual prompt text...
 * ```
 */
object SpecParser {

    data class TaskSpec(
        val title: String,
        val agent: String?,
        val maxRetries: Int?,
        val verify: Boolean?,
        val prompt: String,
    )

    fun parse(file: File): TaskSpec {
        val content = file.readText()
        return parse(content)
    }

    fun parse(content: String): TaskSpec {
        val lines = content.lines()

        // Extract title from first heading
        val title = lines.firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.removePrefix("Task: ")
            ?.trim()
            ?: "untitled"

        // Extract metadata (key: value lines before ## Prompt)
        val metadata = mutableMapOf<String, String>()
        var promptStartIndex = -1

        for ((i, line) in lines.withIndex()) {
            if (line.startsWith("## Prompt")) {
                promptStartIndex = i + 1
                break
            }
            if (line.matches(Regex("^[a-z-]+:\\s*.+"))) {
                val (key, value) = line.split(":", limit = 2).map { it.trim() }
                metadata[key] = value
            }
        }

        // Everything after ## Prompt is the prompt
        val prompt = if (promptStartIndex > 0) {
            lines.drop(promptStartIndex)
                .dropWhile { it.isBlank() }
                .joinToString("\n")
                .trimEnd()
        } else {
            // No ## Prompt heading — use the whole content as prompt
            content.trim()
        }

        return TaskSpec(
            title = title,
            agent = metadata["agent"],
            maxRetries = metadata["max-retries"]?.toIntOrNull(),
            verify = metadata["verify"]?.toBooleanStrictOrNull(),
            prompt = prompt,
        )
    }

    /**
     * Find all task spec files in a directory, sorted by name.
     */
    fun findSpecs(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.name.startsWith("task-") && it.name.endsWith(".md") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }
}
