package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.exec.AgentRunner
import zone.clanker.gradle.exec.CycleDetector
import zone.clanker.gradle.exec.SpecParser
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@UntrackedTask(because = "Exec spawns external agents with dynamic state — must never cache")
abstract class OpenSpecExecTask : DefaultTask() {

    @get:Input @get:Optional
    abstract val prompt: Property<String>

    @get:Input @get:Optional
    abstract val spec: Property<String>

    @get:Input @get:Optional
    abstract val agent: Property<String>

    @get:Input @get:Optional
    abstract val maxRetries: Property<Int>

    @get:Input @get:Optional
    abstract val verify: Property<Boolean>

    @get:Input @get:Optional
    abstract val syncBefore: Property<Boolean>

    @get:Input @get:Optional
    abstract val execTimeout: Property<Int>

    init {
        group = "opsx"
        description = "[tool] Execute an AI agent with a prompt, verify output, retry on failure. " +
            "Output: .opsx/exec/<timestamp>.md. " +
            "Options: -Pprompt=\"...\" (inline prompt), -Pspec=path/to/task.md (task spec file), " +
            "-Pagent=copilot|claude|codex|opencode (override agent), " +
            "-PmaxRetries=3 (retry attempts), -Pverify=true (run opsx-verify after), " +
            "-PsyncBefore=true (fresh opsx-sync before each attempt), -PexecTimeout=300 (seconds). " +
            "Use when: you want Gradle to drive an AI agent end-to-end with retry and verification. " +
            "Chain: opsx-sync → opsx-exec → opsx-verify."
    }

    @TaskAction
    fun execute() {
        val projectDir = project.projectDir
        val outputDir = File(projectDir, ".opsx/exec").also { it.mkdirs() }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"))

        // Resolve what to run
        val taskSpec = when {
            this.spec.isPresent -> {
                val specFile = File(projectDir, this.spec.get())
                if (specFile.isDirectory) {
                    val specs = SpecParser.findSpecs(specFile)
                    if (specs.isEmpty()) throw GradleException("No task-*.md files found in ${this.spec.get()}")
                    executeBatch(specs, outputDir, timestamp)
                    return
                }
                if (!specFile.exists()) throw GradleException("Spec file not found: ${this.spec.get()}")
                SpecParser.parse(specFile)
            }
            this.prompt.isPresent -> {
                SpecParser.TaskSpec(
                    title = "inline",
                    agent = null,
                    maxRetries = null,
                    verify = null,
                    prompt = this.prompt.get(),
                )
            }
            else -> throw GradleException(
                "Either -Pprompt=\"...\" or -Pspec=path/to/task.md is required."
            )
        }

        val result = executeSingle(taskSpec, outputDir, timestamp, "task")
        if (!result) throw GradleException("opsx-exec failed after all retry attempts.")
    }

    private fun executeBatch(specs: List<File>, outputDir: File, timestamp: String) {
        logger.lifecycle("")
        logger.lifecycle("opsx-exec: batch mode — ${specs.size} tasks")
        logger.lifecycle("─".repeat(50))

        for ((i, specFile) in specs.withIndex()) {
            val taskSpec = SpecParser.parse(specFile)
            val taskId = specFile.nameWithoutExtension
            logger.lifecycle("")
            logger.lifecycle("[$taskId] ${taskSpec.title} (${i + 1}/${specs.size})")

            val success = executeSingle(taskSpec, outputDir, timestamp, taskId)
            if (!success) {
                throw GradleException("Task $taskId failed. Stopping batch execution.")
            }
        }

        logger.lifecycle("")
        logger.lifecycle("✓ All ${specs.size} tasks completed successfully.")
    }

    private fun executeSingle(
        taskSpec: SpecParser.TaskSpec,
        outputDir: File,
        timestamp: String,
        taskId: String,
    ): Boolean {
        val projectDir = project.projectDir
        val resolvedMaxRetries = taskSpec.maxRetries
            ?: (if (this@OpenSpecExecTask.maxRetries.isPresent) this@OpenSpecExecTask.maxRetries.get() else 3)
        val resolvedVerify = taskSpec.verify
            ?: (if (this@OpenSpecExecTask.verify.isPresent) this@OpenSpecExecTask.verify.get() else true)
        val resolvedSyncBefore = if (this@OpenSpecExecTask.syncBefore.isPresent) this@OpenSpecExecTask.syncBefore.get() else true
        val resolvedTimeout = if (this@OpenSpecExecTask.execTimeout.isPresent) this@OpenSpecExecTask.execTimeout.get().toLong() else 300L

        // Resolve agent
        val configuredAgents = resolveConfiguredAgents()
        val resolvedAgent = AgentRunner.resolveAgent(
            explicit = taskSpec.agent ?: (if (this@OpenSpecExecTask.agent.isPresent) this@OpenSpecExecTask.agent.get() else null),
            configured = configuredAgents,
        )

        logger.lifecycle("Agent: $resolvedAgent | Retries: $resolvedMaxRetries | Verify: $resolvedVerify")

        val cycleDetector = CycleDetector()
        val attempts = mutableListOf<AgentRunner.AgentResult>()

        for (attempt in 1..resolvedMaxRetries) {
            logger.lifecycle("")
            logger.lifecycle("── Attempt $attempt/$resolvedMaxRetries ──")

            // Fresh sync before each attempt
            if (resolvedSyncBefore) {
                logger.lifecycle("Running opsx-sync...")
                runGradleTask("opsx-sync")
            }

            // Build prompt with retry context
            val fullPrompt = if (attempt == 1) {
                taskSpec.prompt
            } else {
                val lastAttempt = attempts.last()
                buildRetryPrompt(taskSpec.prompt, lastAttempt, attempt, resolvedMaxRetries, attempts)
            }

            // Run agent
            logger.lifecycle("Spawning $resolvedAgent...")
            val result = AgentRunner.run(
                agent = resolvedAgent,
                prompt = fullPrompt,
                workingDir = projectDir,
                timeoutSeconds = resolvedTimeout,
            )
            attempts.add(result)

            // Save output
            val outputFile = File(outputDir, "$timestamp-$taskId-attempt-$attempt.md")
            outputFile.writeText(buildOutputReport(resolvedAgent, attempt, result))
            logger.lifecycle("Output: ${outputFile.relativeTo(projectDir)}")
            logger.lifecycle("Duration: ${result.durationMs / 1000}s | Exit: ${result.exitCode}")

            if (!result.success) {
                logger.warn("Agent exited with code ${result.exitCode}")
                if (attempt < resolvedMaxRetries) {
                    // Check for cycles
                    val errorText = result.stderr.ifBlank { result.stdout }
                    if (cycleDetector.recordAndCheck(errorText)) {
                        val matchAttempt = cycleDetector.findCycleMatch(errorText)
                        logger.error("⚠ Cycle detected: attempt $attempt matches attempt $matchAttempt")
                        writeSummary(outputDir, timestamp, taskId, attempts, "CYCLE_DETECTED")
                        return false
                    }
                }
                continue
            }

            // Verify
            if (resolvedVerify) {
                logger.lifecycle("Running opsx-verify...")
                val verifyPassed = runGradleTaskSafe("opsx-verify")
                if (verifyPassed) {
                    logger.lifecycle("✓ Verification passed")
                    writeSummary(outputDir, timestamp, taskId, attempts, "SUCCESS")
                    return true
                } else {
                    logger.warn("✗ Verification failed")
                    if (attempt < resolvedMaxRetries) {
                        val verifyOutput = getLastVerifyOutput()
                        if (cycleDetector.recordAndCheck(verifyOutput)) {
                            val matchAttempt = cycleDetector.findCycleMatch(verifyOutput)
                            logger.error("⚠ Cycle detected: attempt $attempt matches attempt $matchAttempt")
                            writeSummary(outputDir, timestamp, taskId, attempts, "CYCLE_DETECTED")
                            return false
                        }
                    }
                    continue
                }
            } else {
                // No verify — success if agent exited 0
                writeSummary(outputDir, timestamp, taskId, attempts, "SUCCESS")
                return true
            }
        }

        writeSummary(outputDir, timestamp, taskId, attempts, "FAILED")
        return false
    }

    private fun buildRetryPrompt(
        originalPrompt: String,
        lastAttempt: AgentRunner.AgentResult,
        attemptNum: Int,
        maxAttempts: Int,
        allAttempts: List<AgentRunner.AgentResult>,
    ): String {
        val errorOutput = lastAttempt.stderr.ifBlank { lastAttempt.stdout }
        val previousErrors = allAttempts.mapIndexed { i, a ->
            "Attempt ${i + 1}: exit=${a.exitCode}, ${a.stderr.take(200)}"
        }.joinToString("\n")

        return """
            |$originalPrompt
            |
            |---
            |RETRY CONTEXT (attempt $attemptNum/$maxAttempts):
            |
            |Previous attempt failed. Error output:
            |```
            |${errorOutput.take(2000)}
            |```
            |
            |Fix the issue without reintroducing previous errors.
            |Previous attempts summary:
            |$previousErrors
        """.trimMargin()
    }

    private fun buildOutputReport(agent: String, attempt: Int, result: AgentRunner.AgentResult): String {
        return """
            |# opsx-exec output
            |
            |Agent: $agent
            |Attempt: $attempt
            |Exit code: ${result.exitCode}
            |Duration: ${result.durationMs}ms
            |
            |## stdout
            |
            |```
            |${result.stdout}
            |```
            |
            |## stderr
            |
            |```
            |${result.stderr}
            |```
        """.trimMargin()
    }

    private fun writeSummary(
        outputDir: File,
        timestamp: String,
        taskId: String,
        attempts: List<AgentRunner.AgentResult>,
        status: String,
    ) {
        val summaryFile = File(outputDir, "$timestamp-$taskId-result.md")
        val summary = buildString {
            appendLine("# opsx-exec result: $status")
            appendLine()
            appendLine("Total attempts: ${attempts.size}")
            appendLine("Status: $status")
            appendLine()
            attempts.forEachIndexed { i, a ->
                appendLine("## Attempt ${i + 1}")
                appendLine("- Exit: ${a.exitCode}")
                appendLine("- Duration: ${a.durationMs}ms")
                appendLine("- Success: ${a.success}")
                appendLine()
            }
        }
        summaryFile.writeText(summary)
    }

    private fun resolveConfiguredAgents(): List<String> {
        val ext = project.extensions.findByType(zone.clanker.gradle.OpenSpecExtension::class.java)
        return ext?.tools?.orNull ?: emptyList()
    }

    private fun runGradleTask(taskName: String) {
        val proc = ProcessBuilder(
            "${project.rootDir}/gradlew", taskName,
            "--no-daemon", "-p", project.projectDir.absolutePath,
        )
            .directory(project.rootDir)
            .inheritIO()
            .start()
        val exitCode = proc.waitFor()
        if (exitCode != 0) throw GradleException("$taskName failed with exit code $exitCode")
    }

    private fun runGradleTaskSafe(taskName: String): Boolean {
        return try {
            val proc = ProcessBuilder(
                "${project.rootDir}/gradlew", taskName,
                "--no-daemon", "-p", project.projectDir.absolutePath,
            )
                .directory(project.rootDir)
                .inheritIO()
                .start()
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun getLastVerifyOutput(): String {
        val verifyFile = File(project.projectDir, ".opsx/verify.md")
        return if (verifyFile.exists()) verifyFile.readText() else ""
    }
}
