package zone.clanker.gradle.core

/**
 * Generates short task code prefixes from proposal names and assigns
 * hierarchical codes to task items.
 *
 * Examples:
 * - "task-tracking-dashboard" → "ttd"
 * - "add-user-auth" → "aua"
 * - "fix-login-bug" → "flb"
 * - "single" → "sng"
 */
object TaskCodeGenerator {

    /**
     * Generate a short prefix from a kebab-case proposal name.
     * Takes the first letter of each word.
     * If the result is less than 2 chars, pads from the first word.
     */
    fun prefix(proposalName: String): String {
        val parts = proposalName.split("-").filter { it.isNotBlank() }
        if (parts.isEmpty()) return "xxx"

        val initials = parts.map { it.first().lowercaseChar() }.joinToString("")

        return when {
            initials.length >= 2 -> initials
            parts.isNotEmpty() -> {
                // Single word — take first 3 chars
                val word = parts[0]
                word.take(3).lowercase()
            }
            else -> "xxx"
        }
    }

    /**
     * Assign hierarchical codes to a list of task items that don't have codes yet.
     * Tasks that already have codes are left unchanged.
     *
     * @param codePrefix The prefix to use (e.g., "ttd")
     * @param tasks The task tree to assign codes to
     * @return New task tree with codes assigned
     */
    fun assignCodes(codePrefix: String, tasks: List<TaskItem>): List<TaskItem> {
        return tasks.mapIndexed { index, task ->
            assignCodesRecursive(codePrefix, index + 1, task)
        }
    }

    private fun assignCodesRecursive(prefix: String, number: Int, task: TaskItem): TaskItem {
        val code = if (task.code.isNotBlank()) {
            task.code // keep existing code
        } else {
            "$prefix-$number"
        }

        val children = task.children.mapIndexed { childIndex, child ->
            val childNumber = childIndex + 1
            val childCode = if (child.code.isNotBlank()) {
                child.code
            } else {
                "$prefix-$number.$childNumber"
            }
            child.copy(
                code = childCode,
                children = child.children.mapIndexed { gcIndex, gc ->
                    assignCodesRecursive("$prefix-$number.$childNumber", gcIndex + 1, gc)
                }
            )
        }

        return task.copy(code = code, children = children)
    }

    /**
     * Injects task codes into tasks.md lines.
     * Lines with `- [ ] Some description` become `- [ ] \`ttd-1\` Some description`.
     * Existing codes are replaced with new ones based on the given prefix.
     *
     * @return Modified lines with codes injected
     */
    fun injectCodes(lines: List<String>, codePrefix: String): List<String> {
        // Parse tasks, strip existing codes, then assign fresh ones
        val tasks = TaskParser.parse(lines)
        val stripped = tasks.map { stripCodes(it) }
        val coded = assignCodes(codePrefix, stripped)
        val codeMap = buildCodeMap(coded)

        var taskIndex = 0
        val taskLineRegex = Regex("""^(\s*-\s+\[[ xX~]]\s+)(?:`[^`]+`\s+)?(.+?)(\s*→\s*depends:.+)?$""")

        return lines.map { line ->
            val match = taskLineRegex.matchEntire(line)
            if (match != null && taskIndex < codeMap.size) {
                val prefix = match.groupValues[1]
                val description = match.groupValues[2]
                val depsSuffix = match.groupValues[3]
                val code = codeMap[taskIndex]
                taskIndex++
                "$prefix`$code` $description$depsSuffix"
            } else {
                line
            }
        }
    }

    private fun stripCodes(task: TaskItem): TaskItem =
        task.copy(code = "", children = task.children.map { stripCodes(it) })

    /**
     * Build a flat list of codes in document order from the task tree.
     */
    private fun buildCodeMap(tasks: List<TaskItem>): List<String> {
        val codes = mutableListOf<String>()
        fun walk(items: List<TaskItem>) {
            for (item in items) {
                codes.add(item.code)
                walk(item.children)
            }
        }
        walk(tasks)
        return codes
    }
}
