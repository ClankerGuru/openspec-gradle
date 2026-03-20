package zone.clanker.gradle.tracking

/**
 * Status of a tracked task item.
 */
enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    BLOCKED;

    /** Checkbox with status-specific character */
    val checkbox: String
        get() = when (this) {
            TODO -> "[ ]"
            IN_PROGRESS -> "[/]"
            DONE -> "[x]"
            BLOCKED -> "[~]"
        }

    /** Emoji status indicator placed after the checkbox (visual redundancy) */
    val emoji: String
        get() = when (this) {
            TODO -> ""
            IN_PROGRESS -> "🔄 "
            DONE -> ""
            BLOCKED -> "⛔ "
        }

    /** Display icon for CLI/log output */
    val icon: String
        get() = when (this) {
            TODO -> "⬜"
            IN_PROGRESS -> "🔄"
            DONE -> "✅"
            BLOCKED -> "⛔"
        }
}

/**
 * A single task item parsed from a tasks.md file.
 *
 * @param code Short identifier like "ttd-1.2"
 * @param description Human-readable description
 * @param status Current completion status
 * @param children Nested subtasks
 * @param explicitDeps Cross-cutting dependency codes declared via → depends: syntax
 * @param depth Nesting level (0 = top-level)
 */
/**
 * Inline metadata parsed from task lines (e.g., agent:copilot retries:3 cooldown:60).
 */
data class TaskMetadata(
    val agent: String? = null,
    val retries: Int? = null,
    val cooldown: Int? = null, // seconds
)

data class TaskItem(
    val code: String,
    val description: String,
    val status: TaskStatus,
    val children: List<TaskItem> = emptyList(),
    val explicitDeps: List<String> = emptyList(),
    val depth: Int = 0,
    val metadata: TaskMetadata = TaskMetadata()
) {
    /** Total count of this task + all descendants */
    val totalCount: Int
        get() = 1 + children.sumOf { it.totalCount }

    /** Count of DONE tasks (this + descendants) */
    val doneCount: Int
        get() = (if (status == TaskStatus.DONE) 1 else 0) + children.sumOf { it.doneCount }

    /** Progress as percentage (0-100) */
    val progressPercent: Int
        get() = if (totalCount == 0) 0 else (doneCount * 100) / totalCount

    /** Flattened list of this task + all descendants */
    fun flatten(): List<TaskItem> = listOf(this) + children.flatMap { it.flatten() }

    /** Find a task by code in this tree */
    fun findByCode(targetCode: String): TaskItem? {
        if (code == targetCode) return this
        return children.firstNotNullOfOrNull { it.findByCode(targetCode) }
    }
}

/**
 * A parsed proposal containing its metadata and task tree.
 */
data class Proposal(
    val name: String,
    val prefix: String,
    val tasks: List<TaskItem>
) {
    val totalCount: Int get() = tasks.sumOf { it.totalCount }
    val doneCount: Int get() = tasks.sumOf { it.doneCount }
    val progressPercent: Int
        get() = if (totalCount == 0) 0 else (doneCount * 100) / totalCount

    fun findByCode(code: String): TaskItem? =
        tasks.firstNotNullOfOrNull { it.findByCode(code) }

    fun flatten(): List<TaskItem> = tasks.flatMap { it.flatten() }
}
