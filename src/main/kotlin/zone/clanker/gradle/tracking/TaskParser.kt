package zone.clanker.gradle.tracking

import java.io.File

/**
 * Parses tasks.md Markdown files into structured [TaskItem] trees.
 *
 * Supports:
 * - `- [ ]` (TODO), `- [/]` (IN_PROGRESS), `- [x]`/`- [X]` (DONE), `- [~]` (BLOCKED)
 * - Backtick-wrapped task codes: `ttd-1.2`
 * - Nested tasks via indentation (2 or 4 spaces per level)
 * - Explicit dependencies via `→ depends:` suffix
 */
object TaskParser {

    // Matches: optional whitespace, dash, space, checkbox, space, optional emoji, optional code, description
    private val TASK_LINE_REGEX = Regex(
        """^(\s*)-\s+\[([ xX~/])]\s+(?:([🔄⛔])\s+)?(?:`([^`]+)`\s+)?(.+)$"""
    )

    private val METADATA_REGEX = Regex("""(agent|retries|cooldown):(\S+)""")

    private val DEPENDS_REGEX = Regex(
        """→\s*depends:\s*(.+)$"""
    )

    /**
     * Parse a tasks.md file into a list of top-level [TaskItem]s.
     */
    fun parse(file: File): List<TaskItem> {
        return parse(file.readLines())
    }

    /**
     * Parse lines from a tasks.md file into a list of top-level [TaskItem]s.
     */
    fun parse(lines: List<String>): List<TaskItem> {
        val parsed = mutableListOf<ParsedLine>()

        for (line in lines) {
            val match = TASK_LINE_REGEX.matchEntire(line) ?: continue
            val indent = match.groupValues[1].length
            val checkChar = match.groupValues[2]
            val emojiMarker = match.groupValues[3]
            val code = match.groupValues[4] // empty string if no code
            val rawDescription = match.groupValues[5]

            // Status: emoji markers take priority, then checkbox chars
            val status = when {
                emojiMarker == "🔄" -> TaskStatus.IN_PROGRESS
                emojiMarker == "⛔" -> TaskStatus.BLOCKED
                checkChar == "x" || checkChar == "X" -> TaskStatus.DONE
                checkChar == "~" -> TaskStatus.BLOCKED       // legacy
                checkChar == "/" -> TaskStatus.IN_PROGRESS   // legacy
                else -> TaskStatus.TODO
            }

            // Extract explicit dependencies from description
            val depsMatch = DEPENDS_REGEX.find(rawDescription)
            val explicitDeps = depsMatch?.let { m ->
                m.groupValues[1].split(",").map { it.trim() }.filter { it.isNotBlank() }
            } ?: emptyList()

            // Remove → depends: suffix before parsing metadata
            val withoutDeps = if (depsMatch != null) {
                rawDescription.substring(0, depsMatch.range.first).trim()
            } else {
                rawDescription.trim()
            }

            // Split on em dash to separate metadata from description
            val emDashIdx = withoutDeps.indexOf('\u2014')
            val (metadataStr, description) = if (emDashIdx >= 0) {
                withoutDeps.substring(0, emDashIdx).trim() to withoutDeps.substring(emDashIdx + 1).trim()
            } else {
                "" to withoutDeps
            }

            // Parse inline metadata from the metadata portion
            val metadataMatches = METADATA_REGEX.findAll(metadataStr)
            var agent: String? = null
            var retries: Int? = null
            var cooldown: Int? = null
            for (m in metadataMatches) {
                when (m.groupValues[1]) {
                    "agent" -> agent = m.groupValues[2]
                    "retries" -> retries = m.groupValues[2].toIntOrNull()
                    "cooldown" -> cooldown = m.groupValues[2].toIntOrNull()
                }
            }
            val metadata = TaskMetadata(agent = agent, retries = retries, cooldown = cooldown)

            // Calculate depth from indentation (2 or 4 spaces per level)
            val depth = if (indent >= 4) indent / 4 else if (indent >= 2) indent / 2 else 0

            parsed.add(ParsedLine(depth, code, description, status, explicitDeps, metadata))
        }

        return buildTree(parsed, 0)
    }

    private data class ParsedLine(
        val depth: Int,
        val code: String,
        val description: String,
        val status: TaskStatus,
        val explicitDeps: List<String>,
        val metadata: TaskMetadata = TaskMetadata()
    )

    /**
     * Build a tree from flat parsed lines using depth for hierarchy.
     */
    private fun buildTree(lines: List<ParsedLine>, startIndex: Int): List<TaskItem> {
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<TaskItem>()
        var i = startIndex
        val baseDepth = if (lines.isNotEmpty() && startIndex < lines.size) lines[startIndex].depth else 0

        while (i < lines.size) {
            val line = lines[i]

            if (line.depth < baseDepth) break // back to parent level
            if (line.depth > baseDepth) {
                // This shouldn't happen if we process correctly, skip
                i++
                continue
            }

            // Find children: all following lines with depth > current
            val children = mutableListOf<ParsedLine>()
            var j = i + 1
            while (j < lines.size && lines[j].depth > baseDepth) {
                children.add(lines[j])
                j++
            }

            val childItems = if (children.isNotEmpty()) {
                buildTree(children, 0)
            } else {
                emptyList()
            }

            result.add(
                TaskItem(
                    code = line.code,
                    description = line.description,
                    status = line.status,
                    children = childItems,
                    explicitDeps = line.explicitDeps,
                    depth = line.depth,
                    metadata = line.metadata
                )
            )

            i = j
        }

        return result
    }
}
