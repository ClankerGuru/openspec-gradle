package zone.clanker.gradle.exec

/**
 * Prefixed logger for exec task output.
 * Every line gets a `[taskId]` prefix for clear visual distinction
 * between parent agent output and subprocess output.
 *
 * Accepts a generic log function to avoid coupling to Gradle's Logger API.
 */
class TaskLogger(
    private val taskId: String,
    private val log: (String) -> Unit,
) {
    fun lifecycle(msg: String) = log("[$taskId] $msg")
    fun output(line: String) = log("[$taskId] \u2502 $line")
    fun success(msg: String) = log("[$taskId] \u2713 $msg")
    fun failure(msg: String) = log("[$taskId] \u2717 $msg")
    fun progress(msg: String) = log("[$taskId] \u23f3 $msg")
}
