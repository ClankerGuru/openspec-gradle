package zone.clanker.gradle.exec

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val json = Json { prettyPrint = true }

/**
 * Live execution status for opsx-exec task chains.
 * Written to `.opsx/exec/status.json` during execution so external tools can poll progress.
 */
@Serializable
data class ExecStatus(
    val proposal: String,
    val startedAt: String,
    val currentLevel: Int,
    val tasks: Map<String, TaskExecStatus>,
    val parallel: Boolean = false,
    val totalThreads: Int = 1,
    val activeThreads: Int = 0,
    val verifyMode: String = "build",
) {
    companion object {
        /** Atomic write: write to temp file, then rename. */
        fun write(file: File, status: ExecStatus) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(json.encodeToString(status))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Per-task execution status within an exec run.
 */
@Serializable
data class TaskExecStatus(
    val status: String,
    val attempt: Int? = null,
    val maxAttempts: Int? = null,
    val agent: String? = null,
    val startedAt: String? = null,
    val duration: String? = null,
    val error: String? = null,
    val pid: Long? = null,
    val level: Int? = null,
) {
    companion object {
        const val PENDING = "PENDING"
        const val RUNNING = "RUNNING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
        const val CANCELLED = "CANCELLED"
    }
}
