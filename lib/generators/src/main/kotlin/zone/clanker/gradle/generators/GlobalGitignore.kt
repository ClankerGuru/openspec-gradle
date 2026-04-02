package zone.clanker.gradle.generators

import org.gradle.api.logging.Logger
import java.io.File

/**
 * Manages the global gitignore file (~/.config/git/ignore or core.excludesFile)
 * for OpenSpec-generated files.
 *
 * Generated files are per-developer (different agents per developer),
 * so they belong in the global gitignore, not the project's .gitignore.
 */
object GlobalGitignore {

    // Only ignore directories/files that are 100% ours.
    // We do NOT ignore .github/skills or .github/copilot-instructions.md
    // because teams may commit their own content there.
    // Shared files (CLAUDE.md, copilot-instructions.md, AGENTS.md) use
    // marker sections — the file itself may be team-owned.
    private val IGNORE_PATTERNS = listOf(
        "# OpenSpec: generated context (per-developer, always regenerated)",
        ".opsx/",
        "# OpenSpec: generated agent directories (only if fully ours)",
        ".agents/skills/opsx-*",
        ".agents/skills/srcx-*",
        ".opencode/skills/opsx-*",
        ".opencode/skills/srcx-*",
        ".claude/skills/opsx-*",
        ".claude/skills/srcx-*",
    )

    /**
     * Resolves the global gitignore file path.
     * Checks `git config --global core.excludesFile` first,
     * falls back to `~/.config/git/ignore` and sets core.excludesFile to point there.
     */
    fun resolveGlobalGitignoreFile(): File {
        try {
            val process = ProcessBuilder("git", "config", "--global", "core.excludesFile")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                val expanded = if (output.startsWith("~")) {
                    output.replaceFirst("~", System.getProperty("user.home"))
                } else {
                    output
                }
                return File(expanded)
            }
        } catch (_: Exception) {
            // git not available or config not set
        }

        // Fallback: use XDG default and register it with git so it's always picked up
        val fallback = File(System.getProperty("user.home"), ".config/git/ignore")
        ensureExcludesFileSet(fallback)
        return fallback
    }

    /**
     * Sets `core.excludesFile` in global git config if not already configured.
     * Without this, git won't read `~/.config/git/ignore` on all systems.
     */
    private fun ensureExcludesFileSet(file: File) {
        try {
            ProcessBuilder("git", "config", "--global", "core.excludesFile", file.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Exception) {
            // git not available — best effort
        }
    }

    /**
     * Ensures all OpenSpec ignore patterns are in the global gitignore.
     * Creates the file and parent directories if needed.
     */
    fun ensurePatterns(logger: Logger): Int {
        val file = resolveGlobalGitignoreFile()

        val existingLines = if (file.exists()) {
            file.readLines().map { it.trim() }.toSet()
        } else {
            emptySet()
        }

        val newEntries = IGNORE_PATTERNS.filter { it !in existingLines }

        if (newEntries.isEmpty()) {
            logger.lifecycle("OpenSpec: Global gitignore already up to date (${file.path})")
            return 0
        }

        file.parentFile?.mkdirs()

        val appendContent = buildString {
            if (file.exists() && file.readText().isNotBlank()) {
                appendLine()
            }
            for (entry in newEntries) {
                appendLine(entry)
            }
        }

        file.appendText(appendContent)
        logger.lifecycle("OpenSpec: Updated global gitignore with ${newEntries.count { !it.startsWith("#") }} patterns (${file.path})")
        return newEntries.size
    }
}
