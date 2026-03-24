package zone.clanker.gradle.exec

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tiered build verification for opsx-exec.
 *
 * Three tiers:
 * - **compile** (Tier 1): Compile-only. Fast per-task check (<30s target).
 * - **test** (Tier 2): Unit tests only. Per-batch check (1-5min).
 * - **full** (Tier 3): Full `build`. Pre-commit safety net.
 * - **auto**: Times the first verify; if >threshold, downgrades to compile per-task + test per-batch.
 */
class TieredVerifier(
    private val projectDir: File,
    private val gradlewPath: String,
    private val mode: VerifyMode = VerifyMode.AUTO,
    private val batchSize: Int = 5,
    private val thresholdSeconds: Long = 60,
) {

    private var taskCount: Int = 0
    private var autoResolved: VerifyMode? = null
    private var calibrationDoneMs: Long? = null

    /**
     * The verify mode to use for per-task verification.
     * In AUTO mode, this is resolved after the first calibration run.
     */
    val effectivePerTaskMode: VerifyMode
        get() = when (mode) {
            VerifyMode.AUTO -> autoResolved ?: VerifyMode.AUTO
            else -> mode
        }

    /**
     * Called after each task completes. Returns the verification result.
     * In AUTO/batch modes, runs tier 1 per-task and tier 2 at batch boundaries.
     */
    fun verifyAfterTask(): VerifyResult {
        taskCount++

        return when (mode) {
            VerifyMode.COMPILE -> runTier1()
            VerifyMode.TEST -> runTier2()
            VerifyMode.FULL -> runTier3()
            VerifyMode.AUTO -> verifyAuto()
        }
    }

    /**
     * Run the appropriate verification without incrementing the task counter.
     * Used for explicit verify calls.
     */
    fun verify(tier: VerifyMode): VerifyResult {
        return when (tier) {
            VerifyMode.COMPILE -> runTier1()
            VerifyMode.TEST -> runTier2()
            VerifyMode.FULL -> runTier3()
            VerifyMode.AUTO -> verifyAuto()
        }
    }

    /**
     * Whether a batch boundary has been reached (for external callers to know
     * when a commit should happen).
     */
    fun isBatchBoundary(): Boolean = taskCount > 0 && taskCount % batchSize == 0

    /**
     * Reset the task counter (e.g., after a commit).
     */
    fun resetBatchCounter() {
        taskCount = 0
    }

    // Current task count for testing
    val currentTaskCount: Int get() = taskCount

    private fun verifyAuto(): VerifyResult {
        // First run: calibrate by timing a full build
        if (autoResolved == null) {
            val startMs = System.currentTimeMillis()
            val result = runGradleTask("build")
            val durationMs = System.currentTimeMillis() - startMs
            calibrationDoneMs = durationMs
            val durationSec = durationMs / 1000

            autoResolved = if (durationSec > thresholdSeconds) {
                VerifyMode.COMPILE // downgrade: per-task compile, batch test
            } else {
                VerifyMode.FULL // fast enough: keep full build per-task
            }

            return VerifyResult(
                success = result,
                tier = VerifyMode.FULL,
                durationMs = durationMs,
                message = "Calibration: ${durationSec}s → per-task mode: ${autoResolved}",
            )
        }

        // After calibration
        return when (autoResolved!!) {
            VerifyMode.FULL -> runTier3()
            VerifyMode.COMPILE -> {
                if (isBatchBoundary()) {
                    // Batch boundary: run tier 2
                    runTier2()
                } else {
                    runTier1()
                }
            }
            else -> runTier1()
        }
    }

    private fun runTier1(): VerifyResult {
        val tasks = resolveCompileTasks()
        val startMs = System.currentTimeMillis()
        val success = runGradleTasks(tasks)
        return VerifyResult(
            success = success,
            tier = VerifyMode.COMPILE,
            durationMs = System.currentTimeMillis() - startMs,
            message = "Tier 1 (compile): ${tasks.joinToString(" ")}",
        )
    }

    private fun runTier2(): VerifyResult {
        val tasks = resolveTestTasks()
        val startMs = System.currentTimeMillis()
        val success = runGradleTasks(tasks)
        return VerifyResult(
            success = success,
            tier = VerifyMode.TEST,
            durationMs = System.currentTimeMillis() - startMs,
            message = "Tier 2 (test): ${tasks.joinToString(" ")}",
        )
    }

    private fun runTier3(): VerifyResult {
        val startMs = System.currentTimeMillis()
        val success = runGradleTask("build")
        return VerifyResult(
            success = success,
            tier = VerifyMode.FULL,
            durationMs = System.currentTimeMillis() - startMs,
            message = "Tier 3 (full build)",
        )
    }

    private fun runGradleTask(task: String): Boolean = runGradleTasks(listOf(task))

    private fun runGradleTasks(tasks: List<String>): Boolean {
        return try {
            val command = mutableListOf(gradlewPath).apply {
                addAll(tasks)
                add("--no-daemon")
                add("-p")
                add(projectDir.absolutePath)
            }
            val proc = ProcessBuilder(command)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            // Drain output to prevent blocking
            proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /**
         * Detect whether the project uses Android plugin by checking for
         * common Android build markers.
         */
        fun isAndroidProject(projectDir: File): Boolean {
            val buildFile = File(projectDir, "build.gradle.kts")
            val buildFileGroovy = File(projectDir, "build.gradle")
            val content = when {
                buildFile.exists() -> buildFile.readText()
                buildFileGroovy.exists() -> buildFileGroovy.readText()
                else -> return false
            }
            return content.contains("com.android.application") ||
                content.contains("com.android.library") ||
                content.contains("android {")
        }

        /**
         * Resolve compile tasks based on project type.
         */
        fun resolveCompileTasks(projectDir: File): List<String> {
            return if (isAndroidProject(projectDir)) {
                listOf("compileDebugKotlin")
            } else {
                listOf("compileKotlin")
            }
        }

        /**
         * Resolve test tasks based on project type.
         * For Android: only unit tests, skip instrumented tests.
         */
        fun resolveTestTasks(projectDir: File): List<String> {
            return if (isAndroidProject(projectDir)) {
                listOf("testDebugUnitTest")
            } else {
                listOf("test")
            }
        }
    }

    private fun resolveCompileTasks(): List<String> = Companion.resolveCompileTasks(projectDir)
    private fun resolveTestTasks(): List<String> = Companion.resolveTestTasks(projectDir)
}

/**
 * Verification mode / tier.
 */
enum class VerifyMode {
    /** Tier 1: compile-only check */
    COMPILE,
    /** Tier 2: unit tests only */
    TEST,
    /** Tier 3: full build */
    FULL,
    /** Auto-detect: calibrate on first run, then choose appropriate tier */
    AUTO;

    companion object {
        fun fromString(value: String): VerifyMode = when (value.lowercase()) {
            "compile" -> COMPILE
            "test" -> TEST
            "full" -> FULL
            "auto" -> AUTO
            else -> throw IllegalArgumentException(
                "Unknown verify mode: '$value'. Valid: compile, test, full, auto"
            )
        }
    }
}

/**
 * Result of a verification step.
 */
data class VerifyResult(
    val success: Boolean,
    val tier: VerifyMode,
    val durationMs: Long,
    val message: String,
)
