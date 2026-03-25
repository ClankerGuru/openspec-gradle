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
        "github", "github-copilot" -> listOf("copilot", "-p", prompt, "--yolo", "-s")
        "claude" -> listOf("claude", "-p", prompt, "--dangerously-skip-permissions")
        "codex" -> listOf("codex", "exec", prompt, "--full-auto")
        "opencode" -> listOf("opencode", "run", prompt)
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
            return configured.first()
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
        onOutput: (String) -> Unit = {},
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

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutReader = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                onOutput(line)
                stdout.appendLine(line)
            }
        }
        val stderrReader = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                onOutput(line)
                stderr.appendLine(line)
            }
        }

        // Heartbeat thread — logs every 30s during long operations
        val heartbeat = Thread {
            var elapsed = 0
            while (process.isAlive) {
                Thread.sleep(30_000)
                elapsed += 30
                if (process.isAlive) {
                    onOutput("\u23f3 Still running... ${elapsed}s")
                }
            }
        }
        heartbeat.isDaemon = true

        stdoutReader.start()
        stderrReader.start()
        heartbeat.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            heartbeat.join(1000)
            stdoutReader.join(1000)
            stderrReader.join(1000)
            return AgentResult(
                exitCode = -1,
                stdout = stdout.toString(),
                stderr = stderr.toString() + "\n[TIMEOUT after ${timeoutSeconds}s]",
                durationMs = System.currentTimeMillis() - startTime,
            )
        }

        heartbeat.join(1000)
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
        val pathDirs = System.getenv("PATH")?.split(java.io.File.pathSeparator) ?: return false
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val extensions = if (isWindows) {
            (System.getenv("PATHEXT") ?: ".COM;.EXE;.BAT;.CMD").split(";")
        } else {
            listOf("")
        }
        return pathDirs.any { dir ->
            extensions.any { ext ->
                val file = java.io.File(dir, binary + ext)
                file.exists() && file.canExecute()
            }
        }
    }
}
