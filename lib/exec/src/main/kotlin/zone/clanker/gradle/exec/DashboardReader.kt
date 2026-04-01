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
    val proposalName: String = "",
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
     * Scan all proposal subdirectories under [execDir].
     * Returns a flat list of all agent statuses across all proposals.
     * Each proposal lives in `.opsx/exec/{proposalName}/`.
     */
    fun scan(execDir: File): List<AgentStatus> {
        if (!execDir.exists() || !execDir.isDirectory) return emptyList()

        return execDir.listFiles()
            ?.filter { it.isDirectory && it.name != "answers" }
            ?.flatMap { proposalDir -> scanProposal(proposalDir) }
            ?.sortedBy { it.taskCode }
            ?: emptyList()
    }

    /**
     * Scan a single proposal directory for agent log files.
     */
    fun scanProposal(proposalDir: File): List<AgentStatus> {
        if (!proposalDir.exists() || !proposalDir.isDirectory) return emptyList()

        return proposalDir.listFiles()
            ?.filter { it.isFile && it.extension == "md" && it.name != "dashboard.md" }
            ?.mapNotNull { file -> parseLogFile(file, proposalDir.name) }
            ?.sortedBy { it.taskCode }
            ?: emptyList()
    }

    /**
     * Group agent statuses by proposal name.
     */
    fun scanGrouped(execDir: File): Map<String, List<AgentStatus>> {
        if (!execDir.exists() || !execDir.isDirectory) return emptyMap()

        return execDir.listFiles()
            ?.filter { it.isDirectory && it.name != "answers" }
            ?.associate { proposalDir -> proposalDir.name to scanProposal(proposalDir) }
            ?.filterValues { it.isNotEmpty() }
            ?: emptyMap()
    }

    /**
     * Filter to agents that have an unanswered question.
     */
    fun pendingQuestions(agents: List<AgentStatus>): List<AgentStatus> =
        agents.filter { it.hasQuestion }

    /**
     * Render the top-level dashboard that links to per-proposal dashboards.
     */
    fun renderDashboard(grouped: Map<String, List<AgentStatus>>): String = buildString {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val allAgents = grouped.values.flatten()
        val running = allAgents.filter { it.status == "running" || it.status == "unknown" }
        val questions = pendingQuestions(allAgents)

        appendLine("# OPSX Exec Dashboard")
        appendLine("Updated: $now")
        appendLine()

        if (questions.isNotEmpty()) {
            appendLine("## Questions (${questions.size})")
            appendLine()
            for (q in questions) {
                val text = q.questionText ?: "(no text)"
                appendLine("- **[${q.proposalName}/${q.taskCode}]** $text [-> details](${q.filePath})")
            }
            appendLine()
        }

        appendLine("## Proposals (${grouped.size})")
        appendLine()
        appendLine("| Proposal | Running | Done | Failed | Dashboard |")
        appendLine("|----------|---------|------|--------|-----------|")
        for ((proposal, agents) in grouped.entries.sortedBy { it.key }) {
            val r = agents.count { it.status == "running" || it.status == "unknown" }
            val d = agents.count { it.status == "done" }
            val f = agents.count { it.status == "failed" }
            appendLine("| $proposal | $r | $d | $f | [-> dashboard](.opsx/exec/$proposal/dashboard.md) |")
        }

        if (running.isNotEmpty()) {
            appendLine()
            appendLine("## Active Agents (${running.size})")
            appendLine()
            appendLine("| Proposal | Task | Agent | Elapsed | Last Step |")
            appendLine("|----------|------|-------|---------|-----------|")
            for (a in running) {
                val escapedStep = a.lastStep.replace("|", "\\|")
                appendLine("| ${a.proposalName} | ${a.taskCode} | ${a.agent} | ${a.elapsed} | $escapedStep |")
            }
        }
    }

    /**
     * Render a per-proposal dashboard with full agent details and log links.
     */
    fun renderProposalDashboard(proposalName: String, agents: List<AgentStatus>): String = buildString {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val running = agents.filter { it.status == "running" || it.status == "unknown" }
        val completed = agents.filter { it.status == "done" || it.status == "failed" }
        val questions = pendingQuestions(agents)

        appendLine("# $proposalName — Exec Dashboard")
        appendLine("Updated: $now")
        appendLine("[<- back to top-level dashboard](../dashboard.md)")
        appendLine()

        if (running.isNotEmpty()) {
            appendLine("## Running (${running.size})")
            appendLine()
            appendLine("| Task | Agent | Status | Elapsed | Last Step | Log |")
            appendLine("|------|-------|--------|---------|-----------|-----|")
            for (a in running) {
                val statusLabel = if (a.status == "unknown") "unknown" else "running"
                val escapedStep = a.lastStep.replace("|", "\\|")
                appendLine("| ${a.taskCode} | ${a.agent} | $statusLabel | ${a.elapsed} | $escapedStep | [-> log](${a.taskCode}.md) |")
            }
            appendLine()
        }

        if (questions.isNotEmpty()) {
            appendLine("## Questions (${questions.size})")
            appendLine()
            for (q in questions) {
                val text = q.questionText ?: "(no text)"
                appendLine("- **[${q.taskCode}]** $text")
            }
            appendLine()
        }

        if (completed.isNotEmpty()) {
            appendLine("## Completed (${completed.size})")
            appendLine()
            appendLine("| Task | Duration | Result | Log |")
            appendLine("|------|----------|--------|-----|")
            for (a in completed) {
                val icon = if (a.status == "done") "done" else "FAILED"
                appendLine("| ${a.taskCode} | ${a.elapsed} | $icon | [-> log](${a.taskCode}.md) |")
            }
        }
    }

    /**
     * Scan [execDir], write top-level dashboard and per-proposal dashboards.
     */
    fun writeDashboard(execDir: File) {
        val grouped = scanGrouped(execDir)

        // Top-level dashboard
        val topLevel = File(execDir, "dashboard.md")
        topLevel.writeText(renderDashboard(grouped))

        // Per-proposal dashboards
        for ((proposal, agents) in grouped) {
            val proposalDashboard = File(execDir, "$proposal/dashboard.md")
            proposalDashboard.writeText(renderProposalDashboard(proposal, agents))
        }
    }

    // ---- internal parsing ----

    private fun parseLogFile(file: File, proposalName: String = ""): AgentStatus? {
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
        val relativePath = if (proposalName.isNotEmpty()) {
            ".opsx/exec/$proposalName/${file.name}"
        } else {
            ".opsx/exec/${file.name}"
        }

        return AgentStatus(
            taskCode = taskCode,
            agent = agent,
            status = status,
            elapsed = elapsed,
            lastStep = lastStep,
            hasQuestion = hasQuestion,
            questionText = questionText,
            filePath = relativePath,
            proposalName = proposalName,
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
