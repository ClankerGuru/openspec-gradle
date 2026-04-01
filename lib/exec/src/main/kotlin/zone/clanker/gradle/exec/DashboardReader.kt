package zone.clanker.gradle.exec

import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Status snapshot for a single agent parsed from its `.opsx/exec/{taskCode}.md` log file.
 */
data class AgentStatus(
    val taskCode: String,
    val agent: String,
    val status: String,  // running, done, failed, unknown
    val elapsed: String,
    val lastStep: String,
    val hasQuestion: Boolean,
    val questionText: String?,
    val filePath: String,
)

/**
 * Reads all agent log files from `.opsx/exec/` and assembles a dashboard.
 *
 * Log files are created by [AgentLogWriter] and follow its Markdown format:
 * ```
 * # Task: {code} — {description}
 * Agent: {agent}
 * Status: running|done|failed
 * Started: {ISO timestamp}
 * Elapsed: {duration}
 * Heartbeat: {ISO timestamp}
 *
 * ## Steps
 * - HH:mm:ss Some step...
 *
 * ## Question
 * > question text
 * ```
 */
object DashboardReader {

    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val STALE_THRESHOLD_MINUTES = 3L

    /**
     * List all `.md` agent log files in [execDir], excluding the `answers/` subdirectory,
     * and parse each one into an [AgentStatus].
     */
    fun scan(execDir: File): List<AgentStatus> {
        if (!execDir.exists() || !execDir.isDirectory) return emptyList()

        return execDir.listFiles()
            ?.filter { it.isFile && it.extension == "md" }
            ?.mapNotNull { file -> parseLogFile(file) }
            ?.sortedBy { it.taskCode }
            ?: emptyList()
    }

    /**
     * Filter to agents that have an unanswered question.
     */
    fun pendingQuestions(agents: List<AgentStatus>): List<AgentStatus> =
        agents.filter { it.hasQuestion }

    /**
     * Render a Markdown dashboard table from the agent status list.
     */
    fun renderDashboard(agents: List<AgentStatus>): String = buildString {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val running = agents.filter { it.status == "running" || it.status == "unknown" }
        val completed = agents.filter { it.status == "done" || it.status == "failed" }
        val questions = pendingQuestions(agents)

        appendLine("# OPSX Dashboard")
        appendLine("Updated: $now")
        appendLine()

        // Running agents
        appendLine("## Running Agents (${running.size}/${agents.size})")
        appendLine()
        if (running.isNotEmpty()) {
            appendLine("| Task | Agent | Status | Elapsed | Last Step | Log |")
            appendLine("|------|-------|--------|---------|-----------|-----|")
            for (a in running) {
                val statusLabel = if (a.status == "unknown") "unknown" else "running"
                val escapedStep = a.lastStep.replace("|", "\\|")
                appendLine("| ${a.taskCode} | ${a.agent} | $statusLabel | ${a.elapsed} | $escapedStep | [-> log](${a.filePath}) |")
            }
        } else {
            appendLine("_No running agents._")
        }
        appendLine()

        // Questions
        appendLine("## Questions (${questions.size})")
        appendLine()
        if (questions.isNotEmpty()) {
            for (q in questions) {
                val text = q.questionText ?: "(no text)"
                appendLine("- **[${q.taskCode}]** $text [-> details](${q.filePath}#question)")
            }
        } else {
            appendLine("_No pending questions._")
        }
        appendLine()

        // Completed
        appendLine("## Completed")
        appendLine()
        if (completed.isNotEmpty()) {
            appendLine("| Task | Duration | Result | Log |")
            appendLine("|------|----------|--------|-----|")
            for (a in completed) {
                val icon = if (a.status == "done") "done" else "FAILED"
                appendLine("| ${a.taskCode} | ${a.elapsed} | $icon | [-> log](${a.filePath}) |")
            }
        } else {
            appendLine("_No completed tasks._")
        }
    }

    /**
     * Convenience: scan [execDir], render the dashboard, and write it to [dashboardFile].
     */
    fun writeDashboard(dashboardFile: File, execDir: File) {
        val agents = scan(execDir)
        val content = renderDashboard(agents)
        dashboardFile.parentFile?.mkdirs()
        dashboardFile.writeText(content)
    }

    // ---- internal parsing ----

    private fun parseLogFile(file: File): AgentStatus? {
        val lines = try {
            file.readLines()
        } catch (_: Exception) {
            return null
        }
        if (lines.isEmpty()) return null

        val taskCode = file.nameWithoutExtension
        var agent = ""
        var status = ""
        var elapsed = ""
        var heartbeat: String? = null
        var lastStep = ""
        var questionText: String? = null
        var inSteps = false
        var inQuestion = false

        for (line in lines) {
            // Header fields (before any ## section)
            when {
                line.startsWith("Agent:") -> {
                    agent = line.removePrefix("Agent:").trim()
                    inSteps = false
                    inQuestion = false
                }
                line.startsWith("Status:") -> {
                    status = line.removePrefix("Status:").trim()
                    inSteps = false
                    inQuestion = false
                }
                line.startsWith("Elapsed:") -> {
                    elapsed = line.removePrefix("Elapsed:").trim()
                    inSteps = false
                    inQuestion = false
                }
                line.startsWith("Heartbeat:") -> {
                    heartbeat = line.removePrefix("Heartbeat:").trim()
                    inSteps = false
                    inQuestion = false
                }
                line.startsWith("## Steps") -> {
                    inSteps = true
                    inQuestion = false
                }
                line.startsWith("## Question") -> {
                    inSteps = false
                    inQuestion = true
                }
                line.startsWith("## ") -> {
                    // Any other section ends Steps/Question parsing
                    inSteps = false
                    inQuestion = false
                }
                inSteps && line.startsWith("- ") -> {
                    // Each step line: "- HH:mm:ss message"
                    lastStep = line.removePrefix("- ").trim()
                    // Strip the timestamp prefix (HH:mm:ss ) if present
                    val timePattern = Regex("^\\d{2}:\\d{2}:\\d{2}\\s+")
                    lastStep = lastStep.replace(timePattern, "")
                }
                inQuestion && line.startsWith("> ") -> {
                    val text = line.removePrefix("> ").trim()
                    // Skip the "Asked: ..." metadata line
                    if (!text.startsWith("Asked:")) {
                        questionText = if (questionText == null) text else "$questionText $text"
                    }
                }
            }
        }

        // If heartbeat is stale (>3 minutes old) and status is running, mark as unknown
        if (status == "running" && heartbeat != null) {
            if (isStale(heartbeat)) {
                status = "unknown"
            }
        }

        val hasQuestion = questionText != null
        val relativePath = ".opsx/exec/${file.name}"

        return AgentStatus(
            taskCode = taskCode,
            agent = agent,
            status = status,
            elapsed = elapsed,
            lastStep = lastStep,
            hasQuestion = hasQuestion,
            questionText = questionText,
            filePath = relativePath,
        )
    }

    /**
     * Returns `true` if the given ISO timestamp is more than [STALE_THRESHOLD_MINUTES] old.
     */
    private fun isStale(timestamp: String): Boolean {
        return try {
            val heartbeatTime = LocalDateTime.parse(timestamp, isoFormatter)
            val now = LocalDateTime.now()
            Duration.between(heartbeatTime, now).toMinutes() >= STALE_THRESHOLD_MINUTES
        } catch (_: Exception) {
            // If we can't parse the timestamp, treat as stale
            true
        }
    }
}
