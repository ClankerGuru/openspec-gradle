package zone.clanker.gradle.generators

import zone.clanker.gradle.core.ProposalScanner
import zone.clanker.gradle.core.TaskStatus
import java.io.File

/**
 * Generates dynamic skill files for each task item in open proposals.
 * Every task code (e.g., T1, auth-2.1) becomes a skill (e.g., /opsx-T1).
 */
object TaskCommandGenerator {

    // Only allow alphanumeric, hyphens, dots, and underscores in task codes used as file paths
    private val SAFE_CODE_REGEX = Regex("""^[a-zA-Z0-9][a-zA-Z0-9._-]*$""")

    fun generate(projectDir: File, buildDir: File, tools: List<String>, warnings: List<TaskWarning> = emptyList()): List<GeneratedFile> {
        val warningsByCode = warnings.associateBy { it.taskCode }
        val proposals = ProposalScanner.scan(projectDir)
        if (proposals.isEmpty()) return emptyList()

        val generated = mutableListOf<GeneratedFile>()

        for (proposal in proposals) {
            for (taskItem in proposal.flatten()) {
                if (taskItem.code.isBlank()) continue
                // Sanitize task codes to prevent path traversal
                if (!SAFE_CODE_REGEX.matches(taskItem.code)) continue

                val statusIcon = taskItem.status.icon

                val changeDir = "opsx/changes/${proposal.name}"

                val body = buildString {
                    appendLine("$statusIcon **${taskItem.code}**: ${taskItem.description}")
                    appendLine()
                    appendLine("**Proposal:** ${proposal.name}")
                    appendLine()
                    appendLine("## Context")
                    appendLine()
                    appendLine("Read these files before starting:")
                    appendLine("- `$changeDir/proposal.md` — what & why")
                    appendLine("- `$changeDir/design.md` — how")
                    appendLine("- `$changeDir/tasks.md` — all tasks & progress")
                    appendLine()
                    appendLine("## Implementation")
                    appendLine()
                    appendLine("1. Read the context files above")
                    appendLine("2. Implement this task: **${taskItem.description}**")
                    appendLine("3. When complete, mark done: `./gradlew opsx-${taskItem.code} --set=done`")

                    if (taskItem.explicitDeps.isNotEmpty()) {
                        appendLine()
                        appendLine("## Dependencies")
                        appendLine()
                        appendLine("Complete these first:")
                        for (dep in taskItem.explicitDeps) {
                            appendLine("- `$dep`")
                        }
                    }

                    // Add reconciliation warnings
                    val warning = warningsByCode[taskItem.code]
                    if (warning != null) {
                        appendLine()
                        appendLine("## ⚠️ Reconciliation Warning")
                        appendLine()
                        appendLine("This task references symbols not found in the codebase:")
                        for (missing in warning.missingSymbols) {
                            val suggestions = warning.suggestions[missing] ?: emptyList()
                            if (suggestions.isNotEmpty()) {
                                appendLine("- **`$missing`** — not found. Did you mean: ${suggestions.joinToString(", ") { "`$it`" }}?")
                            } else {
                                appendLine("- **`$missing`** — not found in current source")
                            }
                        }
                        appendLine()
                        appendLine("Review and update this task if the referenced code has changed.")
                    }

                    if (taskItem.children.isNotEmpty()) {
                        appendLine()
                        appendLine("## Subtasks")
                        appendLine()
                        for (child in taskItem.children) {
                            val childIcon = when (child.status) {
                                TaskStatus.DONE -> "✅"
                                TaskStatus.IN_PROGRESS -> "🔄"
                                TaskStatus.TODO -> "⬜"
                                TaskStatus.BLOCKED -> "🚫"
                            }
                            appendLine("- $childIcon `${child.code}`: ${child.description}")
                        }
                    }
                }

                val content = SkillContent(
                    dirName = "opsx-${taskItem.code}",
                    description = "${taskItem.description} (${proposal.name})",
                    instructions = body
                )

                for (toolId in tools) {
                    val adapter = ToolAdapterRegistry.get(toolId) ?: continue
                    val relativePath = adapter.getSkillFilePath("opsx-${taskItem.code}")
                    val file = File(buildDir, relativePath)
                    file.parentFile.mkdirs()
                    file.writeText(adapter.formatSkillFile(content))
                    generated.add(GeneratedFile(relativePath, file))
                }
            }
        }
        return generated
    }
}
