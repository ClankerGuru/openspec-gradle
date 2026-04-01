package zone.clanker.gradle.tasks.workflow

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import zone.clanker.gradle.core.TaskItem
import zone.clanker.gradle.core.TaskParser
import zone.clanker.gradle.core.TaskStatus
import zone.clanker.gradle.core.TaskWriter
import zone.clanker.gradle.core.VerifyAssertion
import zone.clanker.gradle.generators.TaskCommandGenerator
import zone.clanker.gradle.generators.TaskReconciler
import zone.clanker.gradle.generators.ToolAdapterRegistry
import java.io.File

/**
 * Shared task completion pipeline used by both TaskItemTask (manual) and ExecTask (automated).
 *
 * Pipeline: run verify assertions → mark DONE → propagate parent completion
 */
object TaskLifecycle {

    /**
     * Complete a task: run assertions, mark DONE, propagate parents.
     *
     * @param project The Gradle project
     * @param tasksFile The tasks.md file
     * @param code The task code being completed
     * @param taskItem The parsed task item (for assertions)
     * @param skipGate If true, skip assertions and mark as unverified
     * @param verifyCommand Gradle task to run for build-passes assertion
     * @param logger Logger for output
     * @throws GradleException if assertions fail
     */
    fun onTaskCompleted(
        project: Project,
        tasksFile: File,
        code: String,
        taskItem: TaskItem,
        skipGate: Boolean,
        verifyCommand: String,
        logger: Logger,
    ) {
        // 1. Run verify assertions (unless force-skipped)
        if (!skipGate) {
            val assertions = taskItem.verifyAssertions.ifEmpty {
                // Default: just check build passes
                listOf(VerifyAssertion("build-passes", ""))
            }
            val results = AssertionRunner.run(assertions, project, verifyCommand)
            val failures = results.filter { !it.passed }
            if (failures.isNotEmpty()) {
                throw GradleException(AssertionRunner.formatFailures(code, failures))
            }
            logger.lifecycle("Verification passed for '$code'.")
        }

        // 2. Mark DONE
        val verified = !skipGate
        val updated = TaskWriter.updateStatus(tasksFile, code, TaskStatus.DONE, verified)
        if (!updated) {
            throw GradleException("Failed to update task '$code' in tasks.md")
        }

        // 3. Propagate parent completion (parents skip assertions — children already verified)
        val propagated = TaskWriter.propagateCompletion(tasksFile, TaskParser.parse(tasksFile))
        if (propagated.isNotEmpty()) {
            logger.lifecycle("Auto-completed parent tasks: ${propagated.joinToString(", ")}")
        }

        // 4. Post-completion sync: regenerate task skill files (status icons update)
        // Always run sync — even when skipGate is true, skill files must reflect updated status
        runPostCompletionSync(project, logger)

        // 5. Reconcile remaining tasks against current codebase
        // Always run reconciliation — stale references matter regardless of gate
        runReconciliation(project, logger)

        val marker = if (!verified) " (unverified)" else ""
        logger.lifecycle("✅ $code → DONE$marker: ${taskItem.description}")
    }

    /**
     * Lightweight sync after task completion: regenerate task command skill files
     * so status icons reflect the updated state. Skips full context regeneration
     * (that happens on `./gradlew build` via lifecycle hooks).
     */
    private fun runPostCompletionSync(project: Project, logger: Logger) {
        try {
            val toolList = resolveTools(project)
            if (toolList.isEmpty()) return

            val buildDir = File(project.layout.buildDirectory.asFile.get(), "opsx")
            buildDir.mkdirs()

            val taskSkills = TaskCommandGenerator.generate(project.projectDir, buildDir, toolList)

            // Install updated task skill files
            for (generated in taskSkills) {
                val target = File(project.projectDir, generated.relativePath)
                target.parentFile.mkdirs()
                generated.file.copyTo(target, overwrite = true)
            }

            if (taskSkills.isNotEmpty()) {
                logger.lifecycle("Synced ${taskSkills.size} task skill files.")
            }
        } catch (e: Exception) {
            logger.debug("Post-completion sync skipped: ${e.message}")
        }
    }

    /**
     * Run reconciliation on remaining tasks after a task is completed.
     * Checks symbols and file paths. Outputs to console and .opsx/reconcile.md.
     */
    private fun runReconciliation(project: Project, logger: Logger) {
        try {
            val report = TaskReconciler.reconcileFull(project.projectDir)
            if (!report.hasFindings()) return

            val output = buildString {
                appendLine("# Reconciliation Report")
                appendLine()

                if (report.staleSymbols.isNotEmpty()) {
                    appendLine("## Stale Symbol References")
                    appendLine()
                    for (w in report.staleSymbols) {
                        val suggest = if (w.suggestions.values.flatten().isNotEmpty()) {
                            " → did you mean: ${w.suggestions.values.flatten().joinToString(", ")}?"
                        } else ""
                        appendLine("- `${w.taskCode}` (${w.proposalName}): missing ${w.missingSymbols.joinToString(", ")}$suggest")
                    }
                    appendLine()
                }

                if (report.staleFiles.isNotEmpty()) {
                    appendLine("## Stale File References")
                    appendLine()
                    for (w in report.staleFiles) {
                        for (path in w.missingPaths) {
                            val suggest = w.suggestions[path]?.takeIf { it.isNotEmpty() }
                                ?.let { " → did you mean: ${it.joinToString(", ")}?" } ?: ""
                            appendLine("- `${w.taskCode}` (${w.proposalName}): missing `$path`$suggest")
                        }
                    }
                    appendLine()
                }
            }

            // Write to file
            val reconcileFile = File(project.projectDir, ".opsx/reconcile.md")
            reconcileFile.parentFile.mkdirs()
            reconcileFile.writeText(output)

            // Log summary to console
            val totalFindings = report.staleSymbols.size + report.staleFiles.size
            logger.lifecycle("Reconciliation: $totalFindings finding(s) in remaining tasks. See .opsx/reconcile.md")
        } catch (e: Exception) {
            logger.debug("Reconciliation skipped: ${e.message}")
        }
    }

    /**
     * Resolve the verify command from project properties.
     * Checks `zone.clanker.opsx.verifyCommand`, defaults to "assemble".
     */
    fun resolveVerifyCommand(project: Project): String {
        return project.findProperty("zone.clanker.opsx.verifyCommand")
            ?.toString()?.takeIf { it.isNotBlank() }
            ?: "assemble"
    }

    /**
     * Resolve configured agent tools from project properties.
     */
    private fun resolveTools(project: Project): List<String> {
        val raw = project.findProperty("zone.clanker.opsx.agents")?.toString()
            ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "none" }
    }
}
