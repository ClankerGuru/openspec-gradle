package zone.clanker.gradle.core

import java.io.File

/**
 * Updates task status in tasks.md files by task code.
 * Modifies checkbox state in-place.
 */
object TaskWriter {

    // Matches checkbox + optional emoji (with optional agent) + code + rest
    private val TASK_LINE_REGEX = Regex(
        """^(\s*-\s+)\[([ xX~/])]\s+(?:[⬜🔄✅⛔]\s+(?:\([\w-]+\)\s+)?)?(`[^`]+`\s+.+)$"""
    )

    /**
     * Update the status of a task by its code in a tasks.md file.
     *
     * @param file The tasks.md file to update
     * @param code The task code to find (e.g., "ttd-3.1")
     * @param newStatus The new status to set
     * @param verified Whether the task was verified (false = force-completed, adds ⚠️ unverified marker)
     * @param agent Optional agent name to include after the status marker (e.g., "claude")
     * @return true if the task was found and updated
     */
    fun updateStatus(file: File, code: String, newStatus: TaskStatus, verified: Boolean = true, agent: String? = null): Boolean {
        val lines = file.readLines().toMutableList()
        val updated = updateStatusInLines(lines, code, newStatus, verified, agent)
        if (updated) {
            file.writeText(lines.joinToString("\n") + "\n")
        }
        return updated
    }

    /**
     * Update status in a list of lines (modifies in place).
     * @return true if found and updated
     */
    internal fun updateStatusInLines(
        lines: MutableList<String>,
        code: String,
        newStatus: TaskStatus,
        verified: Boolean = true,
        agent: String? = null,
    ): Boolean {
        val codePattern = "`$code`"
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.contains(codePattern)) continue

            val match = TASK_LINE_REGEX.matchEntire(line) ?: continue
            val prefix = match.groupValues[1]    // indent + "- "
            val rest = match.groupValues[3]      // "`code` description..."

            // Strip any existing unverified marker before rebuilding
            val cleanRest = rest.replace(" ⚠️ unverified", "")
            val marker = if (newStatus == TaskStatus.DONE && !verified) " ⚠️ unverified" else ""
            // Include agent name after emoji for IN_PROGRESS status if provided
            val agentSuffix = if (agent != null && newStatus == TaskStatus.IN_PROGRESS) "($agent) " else ""
            lines[i] = "${prefix}${newStatus.checkbox} ${newStatus.emoji}$agentSuffix$cleanRest$marker"
            return true
        }
        return false
    }

    /**
     * Propagate status: if all children of a parent are DONE, mark parent as DONE.
     *
     * @param file The tasks.md file
     * @param tasks Parsed task tree (used to check children status)
     * @return List of codes that were auto-completed
     */
    fun propagateCompletion(file: File, tasks: List<TaskItem>): List<String> {
        val completed = mutableListOf<String>()
        propagateRecursive(file, tasks, completed)
        return completed
    }

    /**
     * Append an attempt log line under a task in the tasks.md file.
     * Inserts `  > **Attempt N** (timestamp): message` right after the task line.
     */
    fun appendAttemptLog(file: File, code: String, attemptNum: Int, message: String): Boolean {
        val lines = file.readLines().toMutableList()
        val codePattern = "`$code`"
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val logLine = "  > **Attempt $attemptNum** ($timestamp): $message"

        for (i in lines.indices) {
            if (!lines[i].contains(codePattern)) continue
            if (TASK_LINE_REGEX.matchEntire(lines[i]) == null) continue

            // Find insertion point: after this line and any existing log lines
            var insertAt = i + 1
            while (insertAt < lines.size && lines[insertAt].trimStart().startsWith("> **Attempt")) {
                insertAt++
            }
            lines.add(insertAt, logLine)
            file.writeText(lines.joinToString("\n") + "\n")
            return true
        }
        return false
    }

    private fun propagateRecursive(file: File, tasks: List<TaskItem>, completed: MutableList<String>) {
        for (task in tasks) {
            if (task.children.isNotEmpty()) {
                // Recurse into children first
                propagateRecursive(file, task.children, completed)

                // Re-parse to get fresh status after child updates
                val freshTasks = TaskParser.parse(file)
                val freshTask = freshTasks.flatMap { it.flatten() }.find { it.code == task.code }

                if (freshTask != null && freshTask.status != TaskStatus.DONE) {
                    val allChildrenDone = freshTask.children.all { it.status == TaskStatus.DONE }
                    if (allChildrenDone && freshTask.children.isNotEmpty()) {
                        updateStatus(file, task.code, TaskStatus.DONE)
                        completed.add(task.code)
                    }
                }
            }
        }
    }
}
