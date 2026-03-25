package zone.clanker.gradle.exec

import java.io.File

/**
 * Build verification for opsx-exec.
 *
 * Leverages Gradle's own incremental build and UP-TO-DATE mechanism.
 * After a small change, `./gradlew build` is fast because Gradle skips
 * unchanged tasks automatically. No special tiering needed.
 *
 * Modes:
 * - **build** (default): Full incremental `./gradlew build`. Fast thanks to caching.
 * - **compile**: Assemble only, skip tests. For when even unit tests are too slow.
 * - **off**: Skip verification entirely. Rely on pre-commit hook.
 */
class BuildVerifier(
    private val projectDir: File,
    private val gradlewPath: String,
    private val mode: VerifyMode = VerifyMode.BUILD,
) {

    /**
     * Run verification. Returns result with success/failure and timing.
     * Uses the Gradle daemon (no --no-daemon) for warm JVM between tasks.
     */
    fun verify(): VerifyResult {
        if (mode == VerifyMode.OFF) {
            return VerifyResult(
                success = true,
                mode = VerifyMode.OFF,
                durationMs = 0,
                message = "Verification skipped (mode=off)",
            )
        }

        val tasks = when (mode) {
            VerifyMode.BUILD -> listOf("build")
            VerifyMode.COMPILE -> listOf("assemble")
        }

        val startMs = System.currentTimeMillis()
        val success = runGradleTasks(tasks)
        val durationMs = System.currentTimeMillis() - startMs

        return VerifyResult(
            success = success,
            mode = mode,
            durationMs = durationMs,
            message = "Verify (${mode.value}): ${tasks.joinToString(" ")} — ${durationMs / 1000}s",
        )
    }

    /**
     * Run verification scoped to specific modules affected by file changes.
     * Example: `:core:build` instead of top-level `build`.
     */
    fun verifyModules(modulePaths: List<String>): VerifyResult {
        if (mode == VerifyMode.OFF) {
            return VerifyResult(true, VerifyMode.OFF, 0, "Verification skipped (mode=off)")
        }

        val taskSuffix = when (mode) {
            VerifyMode.BUILD -> "build"
            VerifyMode.COMPILE -> "assemble"
        }
        val tasks = modulePaths.map { "$it:$taskSuffix" }

        val startMs = System.currentTimeMillis()
        val success = runGradleTasks(tasks)
        val durationMs = System.currentTimeMillis() - startMs

        return VerifyResult(
            success = success,
            mode = mode,
            durationMs = durationMs,
            message = "Verify (${mode.value}): ${tasks.joinToString(" ")} — ${durationMs / 1000}s",
        )
    }

    private fun runGradleTasks(tasks: List<String>): Boolean {
        return try {
            val command = mutableListOf(gradlewPath).apply {
                addAll(tasks)
                add("-p")
                add(projectDir.absolutePath)
            }
            val proc = ProcessBuilder(command)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Verification mode.
 */
enum class VerifyMode(val value: String) {
    /** Full incremental build — fast thanks to Gradle caching */
    BUILD("build"),
    /** Compile only, skip tests */
    COMPILE("compile"),
    /** Skip verification, rely on pre-commit hook */
    OFF("off");

    companion object {
        fun fromString(value: String): VerifyMode = when (value.lowercase()) {
            "build" -> BUILD
            "compile" -> COMPILE
            "off" -> OFF
            else -> throw IllegalArgumentException(
                "Unknown verify mode: '$value'. Valid: build, compile, off"
            )
        }
    }
}

/**
 * Result of a verification step.
 */
data class VerifyResult(
    val success: Boolean,
    val mode: VerifyMode,
    val durationMs: Long,
    val message: String,
)
