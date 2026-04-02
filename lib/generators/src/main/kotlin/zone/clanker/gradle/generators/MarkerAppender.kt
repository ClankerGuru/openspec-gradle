package zone.clanker.gradle.generators

import java.io.File

/**
 * Appends or replaces content between marker comments in a file.
 *
 * Used when a project has team-committed agent instruction files (e.g., CLAUDE.md, AGENTS.md)
 * that cannot be replaced by symlinks. The OPSX-generated content is wrapped in markers
 * so it can be updated without disturbing the rest of the file.
 *
 * Format:
 * ```
 * <!-- OPSX:BEGIN -->
 * {content}
 * <!-- OPSX:END -->
 * ```
 */
object MarkerAppender {

    private const val BEGIN = "<!-- OPSX:BEGIN -->"
    private const val END = "<!-- OPSX:END -->"

    /**
     * Replace content between markers, or append markers + content at the end of the file.
     *
     * If the file does not exist, it is created with just the marker section.
     * If markers already exist, the content between them is replaced.
     * If markers do not exist, the marker section is appended at the end.
     *
     * @param file the target file
     * @param content the content to place between markers (without the markers themselves)
     */
    fun append(file: File, content: String) {
        val markerBlock = "$BEGIN\n$content\n$END"

        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(markerBlock + "\n")
            return
        }

        val existing = file.readText()
        val beginIdx = existing.indexOf(BEGIN)
        val endIdx = existing.indexOf(END)

        if (beginIdx >= 0 && endIdx >= 0 && beginIdx < endIdx) {
            // Replace existing marker section
            val before = existing.substring(0, beginIdx).trimEnd()
            val after = existing.substring(endIdx + END.length).trimStart('\n', '\r')

            val sb = StringBuilder()
            if (before.isNotEmpty()) {
                sb.append(before)
                sb.append("\n\n")
            }
            sb.append(markerBlock)
            if (after.isNotBlank()) {
                sb.append("\n")
                sb.append(after)
            } else {
                sb.append("\n")
            }
            file.writeText(sb.toString())
        } else {
            // No markers — append at end
            val trimmed = existing.trimEnd()
            val sb = StringBuilder()
            if (trimmed.isNotEmpty()) {
                sb.append(trimmed)
                sb.append("\n\n")
            }
            sb.append(markerBlock)
            sb.append("\n")
            file.writeText(sb.toString())
        }
    }

    /**
     * Remove the marker section from the file, if present.
     *
     * Removes everything from BEGIN to END (inclusive) and trims trailing whitespace.
     * If the file becomes empty after removal, it is left as an empty file (not deleted).
     *
     * @param file the target file
     */
    fun remove(file: File) {
        if (!file.exists()) return

        val existing = file.readText()
        val beginIdx = existing.indexOf(BEGIN)
        val endIdx = existing.indexOf(END)

        if (beginIdx < 0 || endIdx < 0 || beginIdx >= endIdx) return

        val before = existing.substring(0, beginIdx)
        val after = existing.substring(endIdx + END.length)
        val cleaned = (before + after).trim()

        if (cleaned.isEmpty()) {
            file.writeText("")
        } else {
            file.writeText(cleaned + "\n")
        }
    }

    /**
     * Check if the file contains OPSX markers.
     *
     * @param file the target file
     * @return true if both BEGIN and END markers are present in the correct order
     */
    fun hasMarkers(file: File): Boolean {
        if (!file.exists()) return false
        val text = file.readText()
        val beginIdx = text.indexOf(BEGIN)
        val endIdx = text.indexOf(END)
        return beginIdx >= 0 && endIdx >= 0 && beginIdx < endIdx
    }
}
