package zone.clanker.gradle.tasks.execution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import zone.clanker.gradle.exec.DashboardReader
import java.io.File

@UntrackedTask(because = "Dashboard reads live agent state — must never cache")
abstract class DashboardTask : DefaultTask() {

    init {
        group = "opsx"
        description = "[tool] Live dashboard for running OPSX agents. " +
            "Shows active agents, progress, elapsed time, and pending questions. " +
            "Output: .opsx/dashboard.md. " +
            "Use when: monitoring parallel agent execution or checking for pending questions."
    }

    @TaskAction
    fun execute() {
        val projectDir = project.projectDir
        val execDir = File(projectDir, ".opsx/exec")
        val agents = DashboardReader.scan(execDir)

        if (agents.isEmpty()) {
            logger.lifecycle("\nNo agents running.\n")
            return
        }

        val running = agents.filter { it.status == "running" || it.status == "unknown" }
        val completed = agents.filter { it.status == "done" || it.status == "failed" }
        val questions = DashboardReader.pendingQuestions(agents)

        logger.lifecycle("")
        logger.lifecycle("OPSX Dashboard")
        logger.lifecycle("─".repeat(60))

        // Running agents
        if (running.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Running (${running.size}):")
            for (a in running) {
                val statusLabel = if (a.status == "unknown") "STALE" else "running"
                val logPath = File(projectDir, a.filePath).absolutePath
                logger.lifecycle("  ${a.taskCode.padEnd(10)} ${a.agent.padEnd(12)} ${statusLabel.padEnd(10)} ${a.elapsed.padEnd(10)} ${a.lastStep}")
                logger.lifecycle("    $logPath")
            }
        }

        // Pending questions
        if (questions.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Questions (${questions.size}):")
            for (q in questions) {
                val text = q.questionText ?: "(no text)"
                logger.lifecycle("  [${q.taskCode}] $text")
                val logPath = File(projectDir, q.filePath).absolutePath
                logger.lifecycle("    $logPath")
            }
        }

        // Completed
        if (completed.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("Completed (${completed.size}):")
            for (a in completed) {
                val icon = if (a.status == "done") "done" else "FAILED"
                val logPath = File(projectDir, a.filePath).absolutePath
                logger.lifecycle("  ${a.taskCode.padEnd(10)} ${a.elapsed.padEnd(10)} $icon")
                logger.lifecycle("    $logPath")
            }
        }

        logger.lifecycle("")

        // Write dashboard.md
        val dashboardFile = File(projectDir, ".opsx/dashboard.md")
        DashboardReader.writeDashboard(dashboardFile, execDir)
        logger.lifecycle("Dashboard written: ${dashboardFile.relativeTo(projectDir)}")
    }
}
