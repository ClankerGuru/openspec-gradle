package zone.clanker.gradle.exec

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wraps ProcessBuilder to spawn AI agent CLIs in non-interactive mode.
 */
object AgentRunner {

    data class AgentResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    /**
     * Build the CLI command for the given agent.
     */
    fun buildCommand(agent: String, prompt: String): List<String> = when (agent) {
        "github", "github-copilot" -> listOf("copilot", "-p", prompt, "--allow-all", "-s")
        "claude" -> listOf("claude", "-p", prompt, "--dangerously-skip-permissions")
        "codex" -> listOf("codex", "exec", prompt, "--full-auto")
        "opencode" -> listOf("opencode", "run", prompt)
        "crush" -> throw IllegalArgumentException(
            "Crush is TUI-only and cannot run in non-interactive mode. Use a different agent."
        )
        else -> throw IllegalArgumentException("Unknown agent: $agent")
    }

    /**
     * Resolve which agent binary to use.
     * Priority: explicit agent param → first from configured list → scan PATH.
     */
    fun resolveAgent(explicit: String?, configured: List<String>): String {
        if (!explicit.isNullOrBlank()) return explicit

        // Use first configured agent
        if (configured.isNotEmpty()) {
            val first = configured.first()
            if (first != "crush") return first
            // Skip crush, try next
            return configured.firstOrNull { it != "crush" }
                ?: throw IllegalArgumentException("No non-interactive agent available. Crush is TUI-only.")
        }

        // Scan PATH for known CLIs
        val knownAgents = listOf("copilot", "claude", "codex", "opencode")
        for (agent in knownAgents) {
            if (isOnPath(agent)) return when (agent) {
                "copilot" -> "github-copilot"
                else -> agent
            }
        }

        throw IllegalArgumentException(
            "No agent CLI found on PATH. Install one of: copilot, claude, codex, opencode"
        )
    }

    /**
     * Execute an agent with the given prompt in the specified working directory.
     */
    fun run(
        agent: String,
        prompt: String,
        workingDir: File,
        timeoutSeconds: Long = 300,
        environment: Map<String, String> = emptyMap(),
    ): AgentResult {
        val command = buildCommand(agent, prompt)
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(false)
            .also { pb ->
                environment.forEach { (k, v) -> pb.environment()[k] = v }
            }
            .start()

        val startTime = System.currentTimeMillis()

        // Read stdout and stderr concurrently to avoid deadlocks
        val stdoutThread = Thread { process.inputStream.bufferedReader().readText() }
        val stderrThread = Thread { process.errorStream.bufferedReader().readText() }

        // Use simple capture approach
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutReader = Thread {
            process.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
        }
        val stderrReader = Thread {
            process.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) }
        }

        stdoutReader.start()
        stderrReader.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutReader.join(1000)
            stderrReader.join(1000)
            return AgentResult(
                exitCode = -1,
                stdout = stdout.toString(),
                stderr = stderr.toString() + "\n[TIMEOUT after ${timeoutSeconds}s]",
                durationMs = System.currentTimeMillis() - startTime,
            )
        }

        stdoutReader.join(5000)
        stderrReader.join(5000)

        return AgentResult(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            durationMs = System.currentTimeMillis() - startTime,
        )
    }

    private fun isOnPath(binary: String): Boolean {
        return try {
            val process = ProcessBuilder("which", binary)
                .redirectErrorStream(true)
                .start()
            process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
