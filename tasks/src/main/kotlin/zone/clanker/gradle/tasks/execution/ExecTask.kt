package zone.clanker.gradle.tasks.execution

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.exec.AgentRunner
import zone.clanker.gradle.exec.CycleDetector
import zone.clanker.gradle.exec.SpecParser
import zone.clanker.gradle.exec.TieredVerifier
import zone.clanker.gradle.exec.VerifyMode
import zone.clanker.gradle.core.*
import zone.clanker.gradle.tasks.workflow.TaskLifecycle
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@UntrackedTask(because = "Exec spawns external agents with dynamic state — must never cache")
abstract class ExecTask : DefaultTask() {

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
    abstract val verifyMode: Property<String>

    @get:Input @get:Optional
    abstract val verifyBatchSize: Property<Int>

    @get:Input @get:Optional
    abstract val verifyThreshold: Property<Int>

    @get:Input @get:Optional
    abstract val syncBefore: Property<Boolean>

    @get:Input @get:Optional
    abstract val execTimeout: Property<Int>

    @get:Input @get:Optional
    abstract val taskCodes: Property<String>

    init {
        group = "opsx"
        description = "[tool] Execute an AI agent with a prompt, verify output, retry on failure. " +
            "Output: .opsx/exec/<timestamp>.md. " +
            "Options: -Pprompt=\"...\" (inline prompt), -Pspec=path/to/task.md (task spec file), " +
            "-Pagent=copilot|claude|codex|opencode (override agent), " +
            "-PmaxRetries=3 (retry attempts), -Pverify=true (run opsx-verify after), " +
            "-Popsx.verify=compile|test|full|auto (tiered verification mode, default: auto), " +
            "-Popsx.verify.batchSize=5 (tasks per batch for tier 2), " +
            "-Popsx.verify.threshold=60 (seconds threshold for auto-downgrade), " +
            "-PsyncBefore=true (fresh opsx-sync before each attempt), -PexecTimeout=600 (seconds). " +
            "Use when: you want Gradle to drive an AI agent end-to-end with retry and verification. " +
            "Chain: opsx-sync → opsx-exec → opsx-verify."
    }

    @TaskAction
    fun execute() {
        // Task chain mode: -Ptask=code1,code2
        if (taskCodes.isPresent) {
            executeTaskChain(taskCodes.get())
            return
        }

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
            ?: (if (this@ExecTask.maxRetries.isPresent) this@ExecTask.maxRetries.get() else 3)
        val resolvedVerify = taskSpec.verify
            ?: (if (this@ExecTask.verify.isPresent) this@ExecTask.verify.get() else true)
        val resolvedSyncBefore = if (this@ExecTask.syncBefore.isPresent) this@ExecTask.syncBefore.get() else true
        val resolvedTimeout = if (this@ExecTask.execTimeout.isPresent) this@ExecTask.execTimeout.get().toLong() else 600L

        // Resolve agent
        val configuredAgents = resolveConfiguredAgents()
        val resolvedAgent = AgentRunner.resolveAgent(
            explicit = taskSpec.agent ?: (if (this@ExecTask.agent.isPresent) this@ExecTask.agent.get() else null),
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

            // Verify (tiered)
            if (resolvedVerify) {
                val verifier = createTieredVerifier()
                logger.lifecycle("Running tiered verification (${verifier.effectivePerTaskMode})...")
                val verifyResult = verifier.verifyAfterTask()
                logger.lifecycle(verifyResult.message + " (${verifyResult.durationMs / 1000}s)")
                if (verifyResult.success) {
                    logger.lifecycle("✓ Verification passed (${verifyResult.tier})")
                    writeSummary(outputDir, timestamp, taskId, attempts, "SUCCESS")
                    return true
                } else {
                    logger.warn("✗ Verification failed (${verifyResult.tier})")
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

    /**
     * Execute a chain of task codes from proposals.
     */
    private fun executeTaskChain(codesStr: String) {
        val projectDir = project.projectDir
        val codes = codesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val outputDir = File(projectDir, ".opsx/exec").also { it.mkdirs() }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"))

        logger.lifecycle("")
        logger.lifecycle("opsx-exec: task chain — ${codes.joinToString(", ")}")
        logger.lifecycle("─".repeat(50))

        // Find proposals and validate all codes exist
        val taskEntries = codes.map { code ->
            val found = ProposalScanner.findProposalByTaskCode(projectDir, code)
                ?: throw GradleException("Task code '$code' not found in any proposal")
            found
        }

        // Validate dependency chain
        val firstProposal = taskEntries.first().first
        val allTasks = firstProposal.flatten()
        val graph = DependencyGraph(firstProposal.tasks)

        for ((i, code) in codes.withIndex()) {
            val task = allTasks.find { it.code == code } ?: continue
            if (task.status == TaskStatus.DONE) continue // will be skipped

            for (depCode in task.explicitDeps) {
                val dep = allTasks.find { it.code == depCode }
                if (dep != null && dep.status != TaskStatus.DONE) {
                    // Check if dep is earlier in chain
                    val depIdx = codes.indexOf(depCode)
                    if (depIdx < 0 || depIdx >= i) {
                        throw GradleException(
                            "Task '$code' depends on '$depCode' which is not DONE and not earlier in chain"
                        )
                    }
                }
            }
        }

        // Execute sequentially
        for ((proposal, taskItem) in taskEntries) {
            val code = taskItem.code
            val tasksFile = File(projectDir, "opsx/changes/${proposal.name}/tasks.md")

            // Re-parse to get fresh status
            val freshTasks = TaskParser.parse(tasksFile)
            val freshTask = freshTasks.flatMap { it.flatten() }.find { it.code == code }
            if (freshTask?.status == TaskStatus.DONE) {
                logger.lifecycle("\n⏭ $code — already DONE, skipping")
                continue
            }

            logger.lifecycle("\n── $code: ${taskItem.description} ──")

            // Mark as IN_PROGRESS
            TaskWriter.updateStatus(tasksFile, code, TaskStatus.IN_PROGRESS)

            // Resolve execution parameters from metadata
            val meta = freshTask?.metadata ?: taskItem.metadata
            val resolvedRetries = meta.retries
                ?: (if (maxRetries.isPresent) maxRetries.get() else 3)
            val resolvedCooldown = meta.cooldown ?: 0
            val resolvedAgent = AgentRunner.resolveAgent(
                explicit = meta.agent ?: (if (agent.isPresent) agent.get() else null),
                configured = resolveConfiguredAgents(),
            )
            val resolvedTimeout = if (execTimeout.isPresent) execTimeout.get().toLong() else 600L

            // Build file references — only project context, NOT the proposal
            // The task description is the scope. The proposal is vision — agents go wild if they see it.
            val contextFiles = mutableListOf<String>()
            if (File(projectDir, ".opsx/context.md").exists()) contextFiles.add(".opsx/context.md")
            if (File(projectDir, ".opsx/tree.md").exists()) contextFiles.add(".opsx/tree.md")

            // Collect previous attempt logs from tasks.md
            val previousLogs = extractAttemptLogs(tasksFile, code)

            var success = false
            for (attempt in 1..resolvedRetries) {
                logger.lifecycle("Attempt $attempt/$resolvedRetries (agent: $resolvedAgent)")

                val prompt = buildTaskPrompt(
                    contextFiles, taskItem.description,
                    previousLogs, attempt, resolvedRetries
                )

                val result = AgentRunner.run(
                    agent = resolvedAgent,
                    prompt = prompt,
                    workingDir = projectDir,
                    timeoutSeconds = resolvedTimeout,
                )

                val outputFile = File(outputDir, "$timestamp-$code-attempt-$attempt.md")
                outputFile.writeText(buildOutputReport(resolvedAgent, attempt, result))

                if (result.success) {
                    // Verify if enabled (tiered)
                    val resolvedVerify = if (verify.isPresent) verify.get() else true
                    if (resolvedVerify) {
                        val verifier = createTieredVerifier()
                        val verifyResult = verifier.verifyAfterTask()
                        logger.lifecycle("${verifyResult.message} (${verifyResult.durationMs / 1000}s)")
                        if (verifyResult.success) {
                            success = true
                            break
                        } else {
                            val msg = "Verification failed (${verifyResult.tier}: ${verifyResult.message})"
                            TaskWriter.appendAttemptLog(tasksFile, code, attempt, msg)
                        }
                    } else {
                        success = true
                        break
                    }
                } else {
                    val errorMsg = result.stderr.take(200).ifBlank { "exit code ${result.exitCode}" }
                    TaskWriter.appendAttemptLog(tasksFile, code, attempt, errorMsg)
                }

                if (attempt < resolvedRetries && resolvedCooldown > 0) {
                    logger.lifecycle("Cooldown: ${resolvedCooldown}s")
                    Thread.sleep(resolvedCooldown * 1000L)
                }
            }

            if (success) {
                // Use shared lifecycle pipeline: assertions → mark DONE → propagate
                try {
                    System.setProperty("opsx.exec.automated", "true")
                    val verifyCommand = TaskLifecycle.resolveVerifyCommand(project)
                    val freshItem = TaskParser.parse(tasksFile).flatMap { it.flatten() }
                        .find { it.code == code } ?: taskItem
                    TaskLifecycle.onTaskCompleted(
                        project, tasksFile, code, freshItem,
                        skipGate = false, verifyCommand, logger
                    )
                    logger.lifecycle("✓ $code — DONE")
                } catch (e: GradleException) {
                    TaskWriter.updateStatus(tasksFile, code, TaskStatus.BLOCKED)
                    logger.lifecycle("✗ $code — BLOCKED (verification failed: ${e.message})")
                    throw GradleException("Task '$code' failed verification — chain stopped")
                } finally {
                    System.clearProperty("opsx.exec.automated")
                }
            } else {
                TaskWriter.updateStatus(tasksFile, code, TaskStatus.BLOCKED)
                logger.lifecycle("✗ $code — BLOCKED after all retries")
                throw GradleException("Task '$code' failed — chain stopped")
            }
        }

        logger.lifecycle("\n✓ Task chain completed successfully")
    }

    private fun buildTaskPrompt(
        contextFiles: List<String>,
        taskDescription: String,
        previousLogs: String,
        attempt: Int,
        maxAttempts: Int,
    ): String = buildString {
        appendLine("Read these files for context before starting:")
        for (file in contextFiles) {
            appendLine("- $file")
        }
        appendLine()
        appendLine("## Task")
        appendLine(taskDescription)
        appendLine()
        appendLine("IMPORTANT: Only implement this specific task. Do not make changes beyond what is described above.")
        appendLine("ONLY modify files mentioned in the task description. Do NOT touch build files, configs, or unrelated code.")
        appendLine("Use OPSX slash commands and Gradle tasks instead of grep, cat, find, or sed. Run ./gradlew opsx for available tasks.")
        appendLine("When done, ensure the build passes with ./gradlew build")
        if (previousLogs.isNotBlank()) {
            appendLine()
            appendLine("## Previous Attempts")
            appendLine(previousLogs)
        }
        if (attempt > 1) {
            appendLine()
            appendLine("---")
            appendLine("RETRY CONTEXT (attempt $attempt/$maxAttempts):")
            appendLine("Fix the issue without reintroducing previous errors. Try a different approach.")
        }
    }

    private fun extractAttemptLogs(tasksFile: File, code: String): String {
        val lines = tasksFile.readLines()
        val codePattern = "`$code`"
        val logs = mutableListOf<String>()
        var found = false
        for (line in lines) {
            if (line.contains(codePattern) && line.trimStart().startsWith("- [")) {
                found = true
                continue
            }
            if (found) {
                if (line.trimStart().startsWith("> **Attempt")) {
                    logs.add(line.trim())
                } else if (line.trimStart().startsWith("- [")) {
                    break // next task
                }
            }
        }
        return logs.joinToString("\n")
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

    private fun createTieredVerifier(): TieredVerifier {
        val modeStr = when {
            verifyMode.isPresent -> verifyMode.get()
            project.hasProperty("opsx.verify") -> project.property("opsx.verify").toString()
            else -> "auto"
        }
        val batch = when {
            verifyBatchSize.isPresent -> verifyBatchSize.get()
            project.hasProperty("opsx.verify.batchSize") ->
                project.property("opsx.verify.batchSize").toString().toInt()
            else -> 5
        }
        val threshold = when {
            verifyThreshold.isPresent -> verifyThreshold.get().toLong()
            project.hasProperty("opsx.verify.threshold") ->
                project.property("opsx.verify.threshold").toString().toLong()
            else -> 60L
        }
        return TieredVerifier(
            projectDir = project.projectDir,
            gradlewPath = resolveGradlew(),
            mode = VerifyMode.fromString(modeStr),
            batchSize = batch,
            thresholdSeconds = threshold,
        )
    }

    private fun resolveConfiguredAgents(): List<String> {
        val ext = project.extensions.findByType(zone.clanker.gradle.core.OpenSpecExtension::class.java)
        return ext?.tools?.orNull ?: emptyList()
    }

    private fun resolveGradlew(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
        return File(project.rootDir, wrapperName).absolutePath
    }

    private fun runGradleTask(taskName: String) {
        val proc = ProcessBuilder(
            resolveGradlew(), taskName,
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
                resolveGradlew(), taskName,
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
