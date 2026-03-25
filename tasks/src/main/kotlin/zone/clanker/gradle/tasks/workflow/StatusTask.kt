package zone.clanker.gradle.tasks.workflow

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import zone.clanker.gradle.generators.TaskReconciler
import zone.clanker.gradle.core.DependencyGraph
import zone.clanker.gradle.core.ProposalScanner
import zone.clanker.gradle.core.Proposal
import zone.clanker.gradle.core.TaskStatus
import zone.clanker.gradle.exec.ExecStatusReader

@UntrackedTask(because = "Reads and displays proposal status from filesystem")
abstract class StatusTask : DefaultTask() {

    @get:Input
    @get:Optional
    @get:Option(option = "proposal", description = "Filter to a specific proposal name")
    abstract val proposal: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Proposal dashboard. " +
            "Output: .opsx/status.md. " +
            "Options: --proposal=<name>. " +
            "Use when: Check proposal progress, find active tasks. " +
            "Chain: opsx-<code> --set=done to work on a task."
    }

    @TaskAction
    fun execute() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val proposals = if (proposal.isPresent) {
            val p = ProposalScanner.findProposal(project.projectDir, proposal.get())
            if (p == null) {
                out.writeText("# Status\n\n> No proposal found: ${proposal.get()}\n")
                logger.lifecycle("OpenSpec: No proposal found: ${proposal.get()}")
                return
            }
            listOf(p)
        } else {
            ProposalScanner.scan(project.projectDir)
        }

        val sb = StringBuilder()

        // Live execution dashboard (if opsx-exec is running)
        val statusFile = java.io.File(project.projectDir, ".opsx/exec/status.json")
        val execStatus = ExecStatusReader.read(statusFile)
        if (execStatus != null) {
            sb.appendLine(ExecStatusReader.renderDashboard(execStatus))
            sb.appendLine()
        }

        if (proposals.isEmpty()) {
            sb.appendLine("# Status")
            sb.appendLine()
            sb.appendLine("> No active proposals. Create one with `./gradlew opsx-propose -Pname=my-feature`")
        } else {
            renderDashboard(sb, proposals)
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }

    private fun renderDashboard(sb: StringBuilder, proposals: List<Proposal>) {
        val totalTasks = proposals.sumOf { it.totalCount }
        val doneTasks = proposals.sumOf { it.doneCount }
        val activeCount = proposals.count { it.progressPercent < 100 }
        val completedCount = proposals.count { it.progressPercent == 100 }

        sb.appendLine("# Status")
        sb.appendLine()
        val overallPct = progressPercent(doneTasks, totalTasks)
        sb.appendLine("${progressBar(overallPct)} **$doneTasks/$totalTasks tasks done**")
        sb.appendLine()
        sb.appendLine("📊 **${proposals.size} proposals** — $activeCount active, $completedCount completed")
        sb.appendLine()

        // Active proposals
        val active = proposals.filter { it.progressPercent < 100 }
        if (active.isNotEmpty()) {
            sb.appendLine("## 🔄 Active")
            sb.appendLine()
            for (p in active) {
                sb.appendLine("### ${p.name}")
                sb.appendLine()
                sb.appendLine("${progressBar(p.progressPercent)} ${p.progressPercent}% — ${p.doneCount}/${p.totalCount} tasks")
                sb.appendLine()
                renderTasks(sb, p.tasks, "")
                sb.appendLine()
            }
        }

        // Completed
        val completed = proposals.filter { it.progressPercent == 100 }
        if (completed.isNotEmpty()) {
            sb.appendLine("## ✅ Completed")
            sb.appendLine()
            for (p in completed) {
                sb.appendLine("- ✅ ~~${p.name}~~")
            }
            sb.appendLine()
        }

        // Task reconciliation warnings
        val warnings = try {
            TaskReconciler.reconcile(project.projectDir)
        } catch (_: Exception) { emptyList() }
        if (warnings.isNotEmpty()) {
            sb.appendLine("## ⚠️ Stale Tasks")
            sb.appendLine()
            sb.appendLine("These tasks reference symbols not found in the codebase:")
            sb.appendLine()
            for (w in warnings) {
                val suggest = w.suggestions.values.flatten().let {
                    if (it.isNotEmpty()) " → did you mean: ${it.joinToString(", ") { s -> "`$s`" }}?" else ""
                }
                sb.appendLine("- `${w.taskCode}` (${w.proposalName}): missing `${w.missingSymbols.joinToString("`, `")}`$suggest")
            }
            sb.appendLine()
        }

        // Dependency cycle warnings
        for (p in proposals) {
            val graph = DependencyGraph(p.tasks)
            val cycles = graph.findCycles()
            if (cycles.isNotEmpty()) {
                sb.appendLine("## ⚠️ Dependency Cycles in '${p.name}'")
                sb.appendLine()
                for (cycle in cycles) {
                    sb.appendLine("- ${cycle.joinToString(" → ")}")
                }
                sb.appendLine()
            }
        }
    }

    private fun renderTasks(sb: StringBuilder, tasks: List<zone.clanker.gradle.core.TaskItem>, indent: String) {
        for (task in tasks) {
            val icon = "- ${task.status.icon}"
            val code = if (task.code.isNotBlank()) "`${task.code}` " else ""
            sb.appendLine("$indent$icon $code${task.description}")
            if (task.children.isNotEmpty()) {
                renderTasks(sb, task.children, "$indent  ")
            }
        }
    }

    private fun progressPercent(done: Int, total: Int): Int =
        if (total == 0) 0 else (done * 100) / total

    private fun progressBar(percent: Int): String {
        val filled = percent / 10
        val empty = 10 - filled
        return "[" + "█".repeat(filled) + "░".repeat(empty) + "] ${percent}%"
    }
}
