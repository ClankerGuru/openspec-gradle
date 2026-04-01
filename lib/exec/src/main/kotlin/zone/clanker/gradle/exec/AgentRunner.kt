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
        val pid: Long? = null,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    /**
     * Build the CLI command for the given agent.
     */
    fun buildCommand(agent: String, prompt: String): List<String> = when (agent) {
        "github", "github-copilot" -> listOf(
            "copilot", "-p", prompt, "--yolo", "-s", "--no-ask-user"
        )
        "claude" -> listOf(
            "claude", "-p", prompt,
            "--dangerously-skip-permissions"
        )
        "codex" -> listOf(
            "codex", "exec", prompt, "--full-auto"
        )
        "opencode" -> listOf(
            "opencode", "run", "--prompt", prompt
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
     *
     * @param logFile optional agent log file — when provided, step detection
     *   writes progress lines via [AgentLogWriter.step] and a daemon thread
     *   calls [AgentLogWriter.heartbeat] every 60 s.
     */
    fun run(
        agent: String,
        prompt: String,
        workingDir: File,
        timeoutSeconds: Long = 300,
        environment: Map<String, String> = emptyMap(),
        onOutput: (String) -> Unit = {},
        logFile: File? = null,
    ): AgentResult {
        val command = buildCommand(agent, prompt)
        val devNull = if (System.getProperty("os.name").lowercase().contains("win"))
            File("NUL") else File("/dev/null")
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(false)
            .redirectInput(devNull)
            .also { pb ->
                environment.forEach { (k, v) -> pb.environment()[k] = v }
            }
            .start()

        val startTime = System.currentTimeMillis()
        val pid = process.pid()

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutReader = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                onOutput(line)
                stdout.appendLine(line)
                if (logFile != null) {
                    detectStep(line)?.let { step -> AgentLogWriter.step(logFile, step) }
                }
            }
        }
        val stderrReader = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                onOutput(line)
                stderr.appendLine(line)
            }
        }

        // Heartbeat thread — logs every 30s during long operations
        // Also writes AgentLogWriter heartbeat every 60s when logFile is set
        val heartbeat = Thread {
            var elapsed = 0
            while (process.isAlive) {
                Thread.sleep(30_000)
                elapsed += 30
                if (process.isAlive) {
                    onOutput("\u23f3 Still running... ${elapsed}s")
                    if (logFile != null && elapsed % 60 == 0) {
                        AgentLogWriter.heartbeat(logFile)
                        AgentLogWriter.updateElapsed(logFile, startTime)
                    }
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
                pid = pid,
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
            pid = pid,
        )
    }

    // ---- step detection ----

    private val stepPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""(?i)\b(?:created|wrote|writing)\b.+\.(kt|java|kts|xml|json|yaml|yml|toml|md|gradle)\b""")
            to { m -> m.value.trim() },
        Regex("""(?i)\bcompil(?:e|ing)\b.+""")
            to { m -> m.value.trim() },
        Regex("""BUILD SUCCESSFUL""")
            to { _ -> "\u2713 BUILD SUCCESSFUL" },
        Regex("""BUILD FAILED""")
            to { _ -> "\u2717 BUILD FAILED" },
        Regex("""(?i)\berror\b:.+""")
            to { m -> m.value.trim() },
    )

    /**
     * Inspect a line of agent output and return a short step description
     * if it looks like meaningful progress, or `null` otherwise.
     */
    internal fun detectStep(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        for ((regex, extract) in stepPatterns) {
            val match = regex.find(trimmed)
            if (match != null) return extract(match)
        }
        return null
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
