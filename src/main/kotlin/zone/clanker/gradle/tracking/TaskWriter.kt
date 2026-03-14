package zone.clanker.gradle.tracking

import java.io.File

/**
 * Updates task status in tasks.md files by task code.
 * Modifies checkbox state in-place.
 */
object TaskWriter {

    private val TASK_LINE_REGEX = Regex(
        """^(\s*-\s+)\[([ xX~])]\s+(`[^`]+`\s+.+)$"""
    )

    /**
     * Update the status of a task by its code in a tasks.md file.
     *
     * @param file The tasks.md file to update
     * @param code The task code to find (e.g., "ttd-3.1")
     * @param newStatus The new status to set
     * @return true if the task was found and updated
     */
    fun updateStatus(file: File, code: String, newStatus: TaskStatus): Boolean {
        val lines = file.readLines().toMutableList()
        val updated = updateStatusInLines(lines, code, newStatus)
        if (updated) {
            file.writeText(lines.joinToString("\n") + "\n")
        }
        return updated
    }

    /**
     * Update status in a list of lines (modifies in place).
     * @return true if found and updated
     */
    internal fun updateStatusInLines(lines: MutableList<String>, code: String, newStatus: TaskStatus): Boolean {
        val codePattern = "`$code`"
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.contains(codePattern)) continue

            val match = TASK_LINE_REGEX.matchEntire(line) ?: continue
            val prefix = match.groupValues[1]    // indent + "- "
            val rest = match.groupValues[3]      // "`code` description..."

            lines[i] = "${prefix}[${newStatus.checkbox.removePrefix("[").removeSuffix("]")}] $rest"
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
