package zone.clanker.gradle.generators

import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.core.Proposal
import zone.clanker.gradle.core.ProposalScanner
import zone.clanker.gradle.core.TaskItem
import zone.clanker.gradle.core.TaskStatus
import java.io.File

/**
 * Result of reconciling a task against the current codebase.
 */
data class TaskWarning(
    val taskCode: String,
    val proposalName: String,
    val description: String,
    val missingSymbols: List<String>,
    val suggestions: Map<String, List<String>> // missing symbol -> possible matches
)

/**
 * Reconciles proposal tasks against the current symbol index.
 * Detects when tasks reference symbols (classes, interfaces, etc.) that no longer exist.
 */
object TaskReconciler {

    // Pattern to extract PascalCase identifiers (likely class/type names)
    // Matches words like UserRepository, BookApi, GetBooksUseCase
    private val SYMBOL_PATTERN = Regex("""\b([A-Z][a-zA-Z0-9]{2,})\b""")

    // Common words that look like PascalCase but aren't symbols
    private val IGNORE_WORDS = setOf(
        "TODO", "DONE", "NOTE", "FIXME", "HACK", "XXX",
        "API", "REST", "HTTP", "JSON", "XML", "SQL", "CSS", "HTML",
        "JWT", "OAuth", "CRUD", "URL", "URI", "UUID",
        "Add", "Create", "Update", "Delete", "Remove", "Fix", "Refactor",
        "Implement", "Build", "Write", "Read", "Test", "Move", "Rename",
        "The", "This", "That", "With", "From", "Into", "Each", "All",
        "New", "Old", "Use", "Set", "Get", "Run", "Let",
        "Integration", "Unit", "End",
    )

    /**
     * Reconcile all proposals against the current symbol index.
     * Returns warnings for tasks that reference missing symbols.
     */
    fun reconcile(projectDir: File): List<TaskWarning> {
        val proposals = ProposalScanner.scan(projectDir)
        if (proposals.isEmpty()) return emptyList()

        // Build symbol index from source files — scan src/ directories conventionally
        val sourceFiles = projectDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .filter { val p = it.path.replace('\\', '/'); p.contains("/src/") }
            .filter { val p = it.path.replace('\\', '/'); !p.contains("/build/") && !p.contains("/.gradle/") }
            .toList()

        if (sourceFiles.isEmpty()) return emptyList()

        val index = SymbolIndex.build(sourceFiles)
        val allSymbolNames = index.symbols.map { it.name.substringAfterLast('.') }.toSet()

        val warnings = mutableListOf<TaskWarning>()

        for (proposal in proposals) {
            for (task in proposal.flatten()) {
                if (task.status == TaskStatus.DONE) continue // skip completed tasks

                val referencedSymbols = extractSymbolNames(task.description)
                if (referencedSymbols.isEmpty()) continue

                val missing = referencedSymbols.filter { it !in allSymbolNames }
                if (missing.isEmpty()) continue

                val suggestions = missing.associateWith { missingName ->
                    findSimilar(missingName, allSymbolNames)
                }

                warnings.add(TaskWarning(
                    taskCode = task.code,
                    proposalName = proposal.name,
                    description = task.description,
                    missingSymbols = missing,
                    suggestions = suggestions
                ))
            }
        }

        return warnings
    }

    /**
     * Extract likely symbol names from a task description.
     */
    fun extractSymbolNames(description: String): List<String> {
        return SYMBOL_PATTERN.findAll(description)
            .map { it.groupValues[1] }
            .filter { it !in IGNORE_WORDS }
            .filter { it.length >= 3 } // skip very short matches
            .distinct()
            .toList()
    }

    /**
     * Find similar symbol names using simple edit distance / prefix matching.
     */
    private fun findSimilar(name: String, allNames: Set<String>, maxResults: Int = 3): List<String> {
        val nameLower = name.lowercase()
        return allNames
            .map { it to similarity(nameLower, it.lowercase()) }
            .filter { it.second > 0.4 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    /**
     * Simple similarity score based on longest common subsequence ratio.
     */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        // Check prefix/suffix overlap
        val prefixLen = a.zip(b).takeWhile { it.first == it.second }.count()
        val maxLen = maxOf(a.length, b.length)

        // Contains check
        if (a.contains(b) || b.contains(a)) return 0.8

        return prefixLen.toDouble() / maxLen
    }
}
