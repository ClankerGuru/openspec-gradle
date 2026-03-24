package zone.clanker.gradle.generators

import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.core.Proposal
import zone.clanker.gradle.core.ProposalScanner
import zone.clanker.gradle.core.TaskItem
import zone.clanker.gradle.core.TaskStatus
import java.io.File

/**
 * Result of reconciling a task's symbols against the current codebase.
 */
data class TaskWarning(
    val taskCode: String,
    val proposalName: String,
    val description: String,
    val missingSymbols: List<String>,
    val suggestions: Map<String, List<String>> // missing symbol -> possible matches
)

/**
 * Warning for stale file path references in a task description.
 */
data class FileWarning(
    val taskCode: String,
    val proposalName: String,
    val missingPaths: List<String>,
    val suggestions: Map<String, List<String>> // missing path -> similar existing paths
)

/**
 * Combined reconciliation report with symbol and file warnings.
 */
data class ReconciliationReport(
    val staleSymbols: List<TaskWarning>,
    val staleFiles: List<FileWarning>,
) {
    fun hasFindings(): Boolean = staleSymbols.isNotEmpty() || staleFiles.isNotEmpty()
}

/**
 * Reconciles proposal tasks against the current symbol index.
 * Detects when tasks reference symbols (classes, interfaces, etc.) that no longer exist.
 */
object TaskReconciler {

    // Pattern to extract PascalCase identifiers (likely class/type names)
    // Matches words like UserRepository, BookApi, GetBooksUseCase
    private val SYMBOL_PATTERN = Regex("""\b([A-Z][a-zA-Z0-9]{2,})\b""")

    // Pattern to extract backtick-wrapped file paths from task descriptions
    private val FILE_PATH_PATTERN = Regex(
        """`([a-zA-Z0-9_./-]+\.(kt|java|kts|xml|yml|yaml|properties|md|json|toml))`"""
    )

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
     * Full reconciliation: check both symbols and file paths.
     * Returns a combined report.
     */
    fun reconcileFull(projectDir: File): ReconciliationReport {
        val symbolWarnings = reconcile(projectDir)
        val fileWarnings = reconcileFiles(projectDir)
        return ReconciliationReport(symbolWarnings, fileWarnings)
    }

    /**
     * Check file path references in task descriptions against the filesystem.
     */
    fun reconcileFiles(projectDir: File): List<FileWarning> {
        val proposals = ProposalScanner.scan(projectDir)
        if (proposals.isEmpty()) return emptyList()

        // Build a list of actual source files for suggestions
        val existingFiles = projectDir.walkTopDown()
            .filter { it.isFile }
            .filter { val p = it.path.replace('\\', '/'); !p.contains("/build/") && !p.contains("/.gradle/") && !p.contains("/.opsx/") }
            .map { it.relativeTo(projectDir).path.replace('\\', '/') }
            .toSet()

        val warnings = mutableListOf<FileWarning>()

        for (proposal in proposals) {
            for (task in proposal.flatten()) {
                if (task.status == TaskStatus.DONE) continue

                val referencedPaths = extractFilePaths(task.description)
                if (referencedPaths.isEmpty()) continue

                val missing = referencedPaths.filter { it !in existingFiles }
                if (missing.isEmpty()) continue

                val suggestions = missing.associateWith { missingPath ->
                    val fileName = missingPath.substringAfterLast('/')
                    existingFiles.filter { it.endsWith("/$fileName") || it.endsWith(fileName) }
                        .take(3)
                }

                warnings.add(FileWarning(
                    taskCode = task.code,
                    proposalName = proposal.name,
                    missingPaths = missing,
                    suggestions = suggestions
                ))
            }
        }

        return warnings
    }

    /**
     * Extract file paths from backtick-wrapped references in a task description.
     */
    fun extractFilePaths(description: String): List<String> {
        return FILE_PATH_PATTERN.findAll(description)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
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
