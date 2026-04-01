package zone.clanker.gradle.exec

import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Per-agent log file writer for `.opsx/exec/{taskCode}.md`.
 *
 * Each agent owns exactly one log file — no file locking needed.
 * The file is a Markdown document with structured sections that get
 * updated in-place as the agent progresses through its task.
 */
object AgentLogWriter {

    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val readableFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * Create the log file with its initial header and first step entry.
     * Files are organized per-proposal: `.opsx/exec/{proposal}/{taskCode}.md`.
     * Returns the created [File] for subsequent calls.
     */
    fun create(execDir: File, proposalName: String, taskCode: String, agent: String, description: String): File {
        val proposalDir = File(execDir, proposalName)
        proposalDir.mkdirs()
        val logFile = File(proposalDir, "$taskCode.md")
        val now = LocalDateTime.now()
        val timestamp = now.format(isoFormatter)
        val time = now.format(readableFormatter)

        logFile.writeText(buildString {
            appendLine("# Task: $taskCode — $description")
            appendLine("Agent: $agent")
            appendLine("Status: running")
            appendLine("Started: $timestamp")
            appendLine("Elapsed: 0s")
            appendLine("Heartbeat: $timestamp")
            appendLine()
            appendLine("## Steps")
            appendLine("- $time Started")
        })

        return logFile
    }

    /**
     * Append a timestamped step entry to the ## Steps section.
     */
    fun step(logFile: File, message: String) {
        val time = LocalDateTime.now().format(readableFormatter)
        logFile.appendText("- $time $message\n")
    }

    /**
     * Update the `Heartbeat:` header line with the current timestamp.
     */
    fun heartbeat(logFile: File) {
        val now = LocalDateTime.now().format(isoFormatter)
        replaceLine(logFile, "Heartbeat:") { "Heartbeat: $now" }
    }

    /**
     * Update the `Elapsed:` header line based on [startTime] (epoch millis).
     */
    fun updateElapsed(logFile: File, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        replaceLine(logFile, "Elapsed:") { "Elapsed: ${formatDuration(elapsed)}" }
    }

    /**
     * Write (or replace) the `## Question` section with a new question.
     */
    fun askQuestion(logFile: File, question: String) {
        val timestamp = LocalDateTime.now().format(isoFormatter)
        val section = buildString {
            appendLine()
            appendLine("## Question")
            appendLine("> $question")
            appendLine("> Asked: $timestamp")
        }

        val content = logFile.readText()
        val existingIdx = content.indexOf("## Question")
        if (existingIdx >= 0) {
            // Replace existing question section up to the next ## or end of file
            val nextSection = content.indexOf("\n## ", existingIdx + 1)
            val replacement = if (nextSection >= 0) {
                content.substring(0, existingIdx) + section.trimEnd() + "\n" + content.substring(nextSection)
            } else {
                content.substring(0, existingIdx) + section.trimEnd() + "\n"
            }
            logFile.writeText(replacement)
        } else {
            logFile.appendText(section)
        }
    }

    /**
     * Read an answer from `.opsx/exec/answers/{taskCode}.md`.
     * Returns the file content if present, then deletes the answer file.
     * Returns `null` if no answer exists.
     */
    fun readAnswer(proposalDir: File, taskCode: String): String? {
        val answerFile = File(proposalDir, "answers/$taskCode.md")
        if (!answerFile.exists()) return null
        val content = answerFile.readText().trim()
        answerFile.delete()
        return content.ifEmpty { null }
    }

    /**
     * Write an answer to `.opsx/exec/answers/{taskCode}.md`.
     * Called by the parent/dashboard when responding to an agent's question.
     */
    fun writeAnswer(proposalDir: File, taskCode: String, answer: String) {
        val answersDir = File(proposalDir, "answers")
        answersDir.mkdirs()
        File(answersDir, "$taskCode.md").writeText(answer)
    }

    /**
     * Poll for an answer file every [intervalMs] milliseconds.
     * Blocks the calling thread until an answer is found or [timeoutMs] elapses.
     * Returns the answer text, or `null` on timeout.
     *
     * When an answer is received, the question section is cleared from [logFile].
     */
    fun pollForAnswer(
        proposalDir: File,
        taskCode: String,
        logFile: File,
        intervalMs: Long = 10_000L,
        timeoutMs: Long = Long.MAX_VALUE,
    ): String? {
        val deadline = if (timeoutMs == Long.MAX_VALUE) Long.MAX_VALUE
            else System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val answer = readAnswer(proposalDir, taskCode)
            if (answer != null) {
                clearQuestion(logFile)
                step(logFile, "Answer received")
                return answer
            }
            val sleepTime = minOf(intervalMs, deadline - System.currentTimeMillis())
            if (sleepTime <= 0) break
            Thread.sleep(sleepTime)
        }
        return null
    }

    /**
     * Remove the `## Question` section from the agent's log file.
     * Called after an answer has been received.
     */
    fun clearQuestion(logFile: File) {
        val content = logFile.readText()
        val questionIdx = content.indexOf("## Question")
        if (questionIdx < 0) return

        val nextSection = content.indexOf("\n## ", questionIdx + 1)
        val cleaned = if (nextSection >= 0) {
            content.substring(0, questionIdx).trimEnd() + "\n" + content.substring(nextSection)
        } else {
            content.substring(0, questionIdx).trimEnd() + "\n"
        }
        logFile.writeText(cleaned)
    }

    /**
     * Mark the task as complete: update Status, write elapsed time,
     * and append the last 50 lines of output to ## Output.
     */
    fun complete(logFile: File, success: Boolean, durationMs: Long, output: String) {
        val status = if (success) "done" else "failed"
        replaceLine(logFile, "Status:") { "Status: $status" }
        replaceLine(logFile, "Elapsed:") { "Elapsed: ${formatDuration(durationMs)}" }

        // Take last 50 lines of output
        val lines = output.lines()
        val tail = if (lines.size > 50) lines.takeLast(50) else lines
        val outputSection = buildString {
            appendLine()
            appendLine("## Output")
            appendLine(tail.joinToString("\n"))
        }

        // Remove existing ## Output section if present, then append new one
        val content = logFile.readText()
        val existingIdx = content.indexOf("## Output")
        if (existingIdx >= 0) {
            logFile.writeText(content.substring(0, existingIdx).trimEnd() + "\n" + outputSection)
        } else {
            logFile.appendText(outputSection)
        }
    }

    // ---- internal helpers ----

    /**
     * Read the log file, find the first line starting with [prefix],
     * replace it using [transform], and write back.
     */
    private fun replaceLine(logFile: File, prefix: String, transform: (String) -> String) {
        val lines = logFile.readLines().toMutableList()
        val idx = lines.indexOfFirst { it.startsWith(prefix) }
        if (idx >= 0) {
            lines[idx] = transform(lines[idx])
            logFile.writeText(lines.joinToString("\n") + "\n")
        }
    }

    private fun formatDuration(millis: Long): String {
        val duration = Duration.ofMillis(millis)
        val seconds = duration.seconds
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
