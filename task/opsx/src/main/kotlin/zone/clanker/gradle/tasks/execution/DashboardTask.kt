package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import zone.clanker.gradle.exec.DashboardReader
import java.io.File

@UntrackedTask(because = "Dashboard reads live agent state — must never cache")
abstract class DashboardTask : DefaultTask() {

    init {
        group = OPSX_GROUP
        description = "[tool] Live dashboard for running OPSX agents. " +
            "Shows active agents grouped by proposal, with links to per-proposal dashboards. " +
            "Output: .opsx/exec/dashboard.md + .opsx/exec/{proposal}/dashboard.md. " +
            "Use when: monitoring parallel agent execution or checking for pending questions."
    }

    @TaskAction
    fun execute() {
        val projectDir = project.projectDir
        val execDir = File(projectDir, ".opsx/exec")
        val grouped = DashboardReader.scanGrouped(execDir)

        if (grouped.isEmpty()) {
            logger.lifecycle("\nNo agents running.\n")
            return
        }

        val allAgents = grouped.values.flatten()
        val running = allAgents.filter { it.status == "running" || it.status == "unknown" }
        val questions = DashboardReader.pendingQuestions(allAgents)

        logger.lifecycle("")
        logger.lifecycle("OPSX Exec Dashboard")
        logger.lifecycle("─".repeat(60))

        // Pending questions (top priority)
        if (questions.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Questions (${questions.size}):")
            for (q in questions) {
                val text = q.questionText ?: "(no text)"
                logger.lifecycle("  [${q.proposalName}/${q.taskCode}] $text")
            }
        }

        // Per-proposal summary
        for ((proposal, agents) in grouped.entries.sortedBy { it.key }) {
            val r = agents.count { it.status == "running" || it.status == "unknown" }
            val d = agents.count { it.status == "done" }
            val f = agents.count { it.status == "failed" }

            logger.lifecycle("")
            logger.lifecycle("$proposal — running: $r, done: $d, failed: $f")

            val proposalRunning = agents.filter { it.status == "running" || it.status == "unknown" }
            for (a in proposalRunning) {
                val statusLabel = if (a.status == "unknown") "STALE" else "running"
                logger.lifecycle("  ${a.taskCode.padEnd(10)} ${a.agent.padEnd(12)} ${statusLabel.padEnd(10)} ${a.elapsed.padEnd(10)} ${a.lastStep}")
            }

            val dashPath = File(projectDir, ".opsx/exec/$proposal/dashboard.md").absolutePath
            logger.lifecycle("  Dashboard: $dashPath")
        }

        logger.lifecycle("")

        // Write dashboards
        DashboardReader.writeDashboard(execDir)
        logger.lifecycle("Top-level: ${File(execDir, "dashboard.md").relativeTo(projectDir)}")
        for (proposal in grouped.keys) {
            logger.lifecycle("  $proposal: .opsx/exec/$proposal/dashboard.md")
        }
    }
}
