package zone.clanker.gradle.exec

import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val json = Json { ignoreUnknownKeys = true }

object ExecStatusReader {

    fun read(file: File): ExecStatus? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ExecStatus>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun renderDashboard(status: ExecStatus): String = buildString {
        val tasks = status.tasks
        val total = tasks.size
        val done = tasks.count { it.value.status == TaskExecStatus.DONE }
        val running = tasks.count { it.value.status == TaskExecStatus.RUNNING }
        val failed = tasks.count { it.value.status == TaskExecStatus.FAILED }
        val pct = if (total == 0) 0 else (done * 100) / total

        val bar = progressBar(pct)
        val header = "  EXEC  ${status.proposal}"
        val stats = "$bar  $done/$total"

        val border = "\u2501".repeat(60)
        appendLine(border)
        appendLine("$header${" ".repeat(maxOf(1, 60 - header.length - stats.length))}$stats")
        appendLine(border)
        appendLine()

        // Table header
        appendLine("  %-7s %-8s %-9s %-6s %-9s %s".format("Code", "Status", "Agent", "Time", "Attempt", "Level"))
        appendLine("  %-7s %-8s %-9s %-6s %-9s %s".format(
            "\u2500".repeat(7), "\u2500".repeat(8), "\u2500".repeat(9),
            "\u2500".repeat(6), "\u2500".repeat(9), "\u2500".repeat(5)
        ))

        // Sort tasks by level then code
        val sorted = tasks.entries.sortedWith(compareBy({ it.value.level ?: Int.MAX_VALUE }, { it.key }))

        for ((code, task) in sorted) {
            val icon = statusIcon(task.status)
            val label = statusLabel(task.status)
            val agent = task.agent ?: "\u2014"
            val time = formatTime(task)
            val attempt = formatAttempt(task)
            val level = task.level?.toString() ?: "\u2014"

            appendLine("  %-7s %s %-5s %-9s %-6s %-9s %s".format(code, icon, label, agent, time, attempt, level))
        }

        appendLine()

        // Footer
        val footerParts = mutableListOf<String>()
        if (status.parallel) {
            footerParts.add("\u26a1 Parallel: ${status.activeThreads}/${status.totalThreads} threads")
        }
        footerParts.add("Verify: ${status.verifyMode}")

        val elapsed = formatElapsed(status.startedAt)
        val startTime = formatStartTime(status.startedAt)

        appendLine("  ${footerParts.joinToString(" \u2502 ")}")
        appendLine("  \ud83d\udd50 Started: $startTime \u2502 Elapsed: $elapsed")
        appendLine(border)
    }

    private fun progressBar(percent: Int): String {
        val filled = percent / 10
        val empty = 10 - filled
        return "[" + "\u2588".repeat(filled) + "\u2591".repeat(empty) + "] ${percent}%"
    }

    private fun statusIcon(status: String): String = when (status) {
        TaskExecStatus.DONE -> "\u2705"
        TaskExecStatus.RUNNING -> "\ud83d\udd04"
        TaskExecStatus.PENDING -> "\u23f3"
        TaskExecStatus.FAILED -> "\u274c"
        TaskExecStatus.CANCELLED -> "\ud83d\udeab"
        else -> "\u2753"
    }

    private fun statusLabel(status: String): String = when (status) {
        TaskExecStatus.DONE -> "done"
        TaskExecStatus.RUNNING -> "run"
        TaskExecStatus.PENDING -> "wait"
        TaskExecStatus.FAILED -> "fail"
        TaskExecStatus.CANCELLED -> "skip"
        else -> "?"
    }

    private fun formatTime(task: TaskExecStatus): String {
        if (task.duration != null) return task.duration
        if (task.status == TaskExecStatus.RUNNING && task.startedAt != null) {
            return try {
                val start = LocalDateTime.parse(task.startedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val now = LocalDateTime.now()
                val seconds = maxOf(0, Duration.between(start, now).seconds)
                formatSeconds(seconds)
            } catch (_: Exception) {
                "\u2014"
            }
        }
        return "\u2014"
    }

    private fun formatAttempt(task: TaskExecStatus): String {
        val attempt = task.attempt ?: return "\u2014"
        val max = task.maxAttempts ?: return "$attempt"
        return "$attempt/$max"
    }

    private fun formatElapsed(startedAt: String): String {
        return try {
            val start = LocalDateTime.parse(startedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val now = LocalDateTime.now()
            val seconds = maxOf(0, Duration.between(start, now).seconds)
            formatSeconds(seconds)
        } catch (_: Exception) {
            "\u2014"
        }
    }

    private fun formatStartTime(startedAt: String): String {
        return try {
            val dt = LocalDateTime.parse(startedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dt.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (_: Exception) {
            startedAt
        }
    }

    private fun formatSeconds(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
