package zone.clanker.gradle.tasks.execution

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.exec.AgentLogWriter
import zone.clanker.gradle.exec.AgentRunner
import zone.clanker.gradle.exec.BuildVerifier
import zone.clanker.gradle.exec.CycleDetector
import zone.clanker.gradle.exec.DashboardReader
import zone.clanker.gradle.exec.SpecParser
import zone.clanker.gradle.exec.ExecStatus
import zone.clanker.gradle.exec.LevelScheduler
import zone.clanker.gradle.exec.TaskExecStatus
import zone.clanker.gradle.exec.TaskLogger
import zone.clanker.gradle.exec.VerifyMode
import zone.clanker.gradle.core.*
import zone.clanker.gradle.tasks.workflow.TaskLifecycle
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

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
    abstract val execTimeout: Property<Int>

    @get:Input @get:Optional
    abstract val taskCodes: Property<String>

    @get:Input @get:Optional
    abstract val verifyMode: Property<String>

    @get:Input @get:Optional
    abstract val parallel: Property<Boolean>

    @get:Input @get:Optional
    abstract val parallelThreads: Property<Int>

    init {
        group = "opsx"
        description = "[tool] Execute an AI agent with a prompt, verify output, retry on failure. " +
            "Output: .opsx/exec/<timestamp>.md. " +
            "Options: -Pprompt=\"...\" (inline prompt), -Pspec=path/to/task.md (task spec file), " +
            "-Pagent=copilot|claude|codex|opencode (override agent), " +
            "-PmaxRetries=3 (retry attempts), -Pverify=true (run srcx-verify after), " +
            "-Popsx.verify=build|compile|off (verification mode, default: build), " +
            "-PexecTimeout=600 (seconds). " +
            "Use when: you want Gradle to drive an AI agent end-to-end with retry and verification. " +
            "Chain: opsx-exec → srcx-verify."
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
        val resolvedTimeout = if (this@ExecTask.execTimeout.isPresent) this@ExecTask.execTimeout.get().toLong() else 600L

        // Resolve agent
        val configuredAgents = resolveConfiguredAgents()
        val resolvedAgent = AgentRunner.resolveAgent(
            explicit = taskSpec.agent ?: (if (this@ExecTask.agent.isPresent) this@ExecTask.agent.get() else null),
            configured = configuredAgents,
        )

        val taskLog = TaskLogger(taskId) { msg -> logger.lifecycle(msg) }
        taskLog.lifecycle("Agent: $resolvedAgent | Retries: $resolvedMaxRetries | Verify: $resolvedVerify")

        val cycleDetector = CycleDetector()
        val attempts = mutableListOf<AgentRunner.AgentResult>()
        val startMs = System.currentTimeMillis()
        val proposalName = "adhoc"
        val proposalDir = File(outputDir, proposalName).also { it.mkdirs() }
        val logFile = AgentLogWriter.create(outputDir, proposalName, taskId, resolvedAgent, taskSpec.title)

        for (attempt in 1..resolvedMaxRetries) {
            taskLog.lifecycle("")
            taskLog.lifecycle("── Attempt $attempt/$resolvedMaxRetries ──")

            // Build prompt with retry context
            val fullPrompt = if (attempt == 1) {
                taskSpec.prompt
            } else {
                val lastAttempt = attempts.last()
                buildRetryPrompt(taskSpec.prompt, lastAttempt, attempt, resolvedMaxRetries, attempts)
            }

            // Run agent
            warnIfTooManyAgents(outputDir)
            taskLog.progress("Spawning $resolvedAgent...")
            val result = AgentRunner.run(
                agent = resolvedAgent,
                prompt = fullPrompt,
                workingDir = projectDir,
                timeoutSeconds = resolvedTimeout,
                onOutput = taskLog::output,
                logFile = logFile,
            )
            attempts.add(result)

            // Save output
            val outputFile = File(proposalDir, "$taskId-attempt-$attempt.md")
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
                        AgentLogWriter.complete(logFile, false, System.currentTimeMillis() - startMs, attempts.last().stdout)
                        writeSummary(proposalDir, timestamp, taskId, attempts, "CYCLE_DETECTED")
                        return false
                    }
                }
                continue
            }

            // Verify (incremental build)
            if (resolvedVerify) {
                val verifier = createBuildVerifier()
                val verifyResult = verifier.verify()
                logger.lifecycle(verifyResult.message)
                if (verifyResult.success) {
                    logger.lifecycle("✓ Verification passed")
                    AgentLogWriter.complete(logFile, true, System.currentTimeMillis() - startMs, attempts.last().stdout)
                    writeSummary(proposalDir, timestamp, taskId, attempts, "SUCCESS")
                    return true
                } else {
                    logger.warn("✗ Verification failed")
                    if (attempt < resolvedMaxRetries) {
                        val verifyOutput = getLastVerifyOutput()
                        if (cycleDetector.recordAndCheck(verifyOutput)) {
                            val matchAttempt = cycleDetector.findCycleMatch(verifyOutput)
                            logger.error("⚠ Cycle detected: attempt $attempt matches attempt $matchAttempt")
                            AgentLogWriter.complete(logFile, false, System.currentTimeMillis() - startMs, attempts.last().stdout)
                            writeSummary(proposalDir, timestamp, taskId, attempts, "CYCLE_DETECTED")
                            return false
                        }
                    }
                    continue
                }
            } else {
                // No verify — success if agent exited 0
                AgentLogWriter.complete(logFile, true, System.currentTimeMillis() - startMs, attempts.last().stdout)
                writeSummary(proposalDir, timestamp, taskId, attempts, "SUCCESS")
                return true
            }
        }

        AgentLogWriter.complete(logFile, false, System.currentTimeMillis() - startMs, attempts.lastOrNull()?.stdout ?: "")
        writeSummary(proposalDir, timestamp, taskId, attempts, "FAILED")
        return false
    }

    /**
     * Execute a chain of task codes from proposals.
     * Uses LevelScheduler for DAG-based ordering and optional parallel execution.
     */
    private fun executeTaskChain(codesStr: String) {
        val projectDir = project.projectDir
        val codes = codesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val outputDir = File(projectDir, ".opsx/exec").also { it.mkdirs() }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"))
        val statusFile = File(projectDir, ".opsx/exec/status.json")
        val isParallel = parallel.isPresent && parallel.get()
        val threads = if (parallelThreads.isPresent) parallelThreads.get() else 4
        val resolvedVerifyMode = when {
            verifyMode.isPresent -> verifyMode.get()
            project.hasProperty("opsx.verify") -> project.property("opsx.verify").toString()
            else -> "build"
        }

        logger.lifecycle("")
        logger.lifecycle("opsx-exec: task chain — ${codes.joinToString(", ")}${if (isParallel) " (parallel, $threads threads)" else ""}")
        logger.lifecycle("─".repeat(50))

        // Find proposals and validate all codes exist
        val taskEntries = codes.map { code ->
            val found = ProposalScanner.findProposalByTaskCode(projectDir, code)
                ?: throw GradleException("Task code '$code' not found in any proposal")
            found
        }

        // Get all tasks from the proposal for scheduling
        val firstProposal = taskEntries.first().first
        val allTasks = firstProposal.flatten()

        // Filter to only requested codes
        val requestedTasks = allTasks.filter { it.code in codes }

        // Use LevelScheduler for DAG-based level grouping
        val levels = LevelScheduler(requestedTasks).schedule()

        logger.lifecycle("Scheduled ${requestedTasks.size} tasks across ${levels.size} levels")
        for ((i, level) in levels.withIndex()) {
            logger.lifecycle("  Level $i: ${level.map { it.code }.joinToString(", ")}")
        }

        // Initialize ExecStatus (concurrent map for thread-safe parallel updates)
        val taskStatusMap: MutableMap<String, TaskExecStatus> = ConcurrentHashMap(
            codes.associateWith { TaskExecStatus(status = TaskExecStatus.PENDING) }
        )
        val startedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        fun writeStatus(levelIdx: Int) = writeExecStatus(
            statusFile, firstProposal.name, startedAt, levelIdx, taskStatusMap,
            isParallel, threads, resolvedVerifyMode,
        )
        writeStatus(0)

        // Execute levels
        for ((levelIdx, level) in levels.withIndex()) {
            logger.lifecycle("\n── Level $levelIdx (${level.size} tasks) ──")
            writeStatus(levelIdx)

            if (isParallel && level.size > 1) {
                executeLevelParallel(level, taskEntries, outputDir, timestamp, statusFile,
                    firstProposal.name, startedAt, levelIdx, taskStatusMap, threads, resolvedVerifyMode)
            } else {
                for (taskItem in level) {
                    executeChainTask(taskItem, taskEntries, outputDir, timestamp, statusFile,
                        firstProposal.name, startedAt, levelIdx, taskStatusMap, resolvedVerifyMode)
                }
            }

        }

        // Clean up status file
        statusFile.delete()
        logger.lifecycle("\n✓ Task chain completed successfully")
    }

    /**
     * Execute all tasks in a level in parallel using a thread pool.
     */
    private fun executeLevelParallel(
        level: List<TaskItem>,
        taskEntries: List<Pair<Proposal, TaskItem>>,
        outputDir: File,
        timestamp: String,
        statusFile: File,
        proposalName: String,
        startedAt: String,
        levelIdx: Int,
        taskStatusMap: MutableMap<String, TaskExecStatus>,
        threads: Int,
        verifyModeStr: String = "build",
    ) {
        val pool = Executors.newFixedThreadPool(minOf(threads, level.size))
        val futures = mutableListOf<Pair<String, Future<*>>>()

        try {
            for (taskItem in level) {
                val future = pool.submit {
                    executeChainTask(taskItem, taskEntries, outputDir, timestamp, statusFile,
                        proposalName, startedAt, levelIdx, taskStatusMap, verifyModeStr)
                }
                futures.add(taskItem.code to future)
            }

            // Await all, fail-fast on first failure
            for ((code, future) in futures) {
                try {
                    future.get()
                } catch (e: Exception) {
                    // Cancel remaining futures
                    for ((_, remaining) in futures) {
                        remaining.cancel(true)
                    }
                    // Mark uncompleted tasks as CANCELLED
                    for (item in level) {
                        val current = taskStatusMap[item.code]
                        if (current?.status == TaskExecStatus.PENDING || current?.status == TaskExecStatus.RUNNING) {
                            taskStatusMap[item.code] = TaskExecStatus(status = TaskExecStatus.CANCELLED)
                        }
                    }
                    writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
                        isParallel = true, totalThreads = threads, verifyModeStr = verifyModeStr)
                    val cause = e.cause ?: e
                    throw if (cause is GradleException) cause else GradleException("Task '$code' failed: ${cause.message}", cause)
                }
            }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Execute a single task within a chain (used by both sequential and parallel paths).
     */
    private fun executeChainTask(
        taskItem: TaskItem,
        taskEntries: List<Pair<Proposal, TaskItem>>,
        outputDir: File,
        timestamp: String,
        statusFile: File,
        proposalName: String,
        startedAt: String,
        levelIdx: Int,
        taskStatusMap: MutableMap<String, TaskExecStatus>,
        verifyModeStr: String = "build",
    ) {
        val projectDir = project.projectDir
        val code = taskItem.code
        val entry = taskEntries.find { it.second.code == code }
            ?: throw GradleException("Task entry not found for '$code'")
        val (proposal, _) = entry
        val tasksFile = File(projectDir, "opsx/changes/${proposal.name}/tasks.md")

        // Re-parse to get fresh status
        val freshTasks = TaskParser.parse(tasksFile)
        val freshTask = freshTasks.flatMap { it.flatten() }.find { it.code == code }
        if (freshTask?.status == TaskStatus.DONE) {
            logger.lifecycle("\n⏭ $code — already DONE, skipping")
            taskStatusMap[code] = TaskExecStatus(status = TaskExecStatus.DONE, level = levelIdx)
            writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
                verifyModeStr = verifyModeStr)
            return
        }

        val taskLog = TaskLogger(code) { msg -> logger.lifecycle(msg) }
        taskLog.lifecycle("${taskItem.description}")

        // Update status to RUNNING
        val taskStartMs = System.currentTimeMillis()
        val taskStartedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        taskStatusMap[code] = TaskExecStatus(
            status = TaskExecStatus.RUNNING,
            startedAt = taskStartedAt,
            level = levelIdx,
        )
        writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
            verifyModeStr = verifyModeStr)

        // Mark as IN_PROGRESS in tasks.md
        TaskWriter.updateStatus(tasksFile, code, TaskStatus.IN_PROGRESS)

        // Resolve execution parameters
        val meta = freshTask?.metadata ?: taskItem.metadata
        val resolvedRetries = meta.retries
            ?: (if (maxRetries.isPresent) maxRetries.get() else 3)
        val resolvedCooldown = meta.cooldown ?: 0
        val resolvedAgent = AgentRunner.resolveAgent(
            explicit = meta.agent ?: (if (agent.isPresent) agent.get() else null),
            configured = resolveConfiguredAgents(),
        )
        val resolvedTimeout = if (execTimeout.isPresent) execTimeout.get().toLong() else 600L

        val contextFiles = mutableListOf<String>()
        if (File(projectDir, ".opsx/context.md").exists()) contextFiles.add(".opsx/context.md")
        if (File(projectDir, ".opsx/tree.md").exists()) contextFiles.add(".opsx/tree.md")

        val previousLogs = extractAttemptLogs(tasksFile, code)
        val logFile = AgentLogWriter.create(outputDir, proposalName, code, resolvedAgent, taskItem.description)

        var success = false
        var lastStdout = ""
        for (attempt in 1..resolvedRetries) {
            taskStatusMap[code] = TaskExecStatus(
                status = TaskExecStatus.RUNNING,
                attempt = attempt,
                maxAttempts = resolvedRetries,
                agent = resolvedAgent,
                startedAt = taskStartedAt,
                level = levelIdx,
            )
            writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
                verifyModeStr = verifyModeStr)

            warnIfTooManyAgents(outputDir)
            taskLog.progress("Attempt $attempt/$resolvedRetries (agent: $resolvedAgent)")

            val prompt = buildTaskPrompt(
                contextFiles, taskItem.description,
                previousLogs, attempt, resolvedRetries
            )

            val result = AgentRunner.run(
                agent = resolvedAgent,
                prompt = prompt,
                workingDir = projectDir,
                timeoutSeconds = resolvedTimeout,
                onOutput = taskLog::output,
                logFile = logFile,
            )
            lastStdout = result.stdout

            // Update status with PID from the completed process
            taskStatusMap[code] = TaskExecStatus(
                status = TaskExecStatus.RUNNING,
                attempt = attempt,
                maxAttempts = resolvedRetries,
                agent = resolvedAgent,
                startedAt = taskStartedAt,
                pid = result.pid,
                level = levelIdx,
            )
            writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
                verifyModeStr = verifyModeStr)

            val proposalOutputDir = File(outputDir, proposalName).also { it.mkdirs() }
            val outputFile = File(proposalOutputDir, "$code-attempt-$attempt.md")
            outputFile.writeText(buildOutputReport(resolvedAgent, attempt, result))

            if (result.success) {
                val resolvedVerify = if (verify.isPresent) verify.get() else true
                if (resolvedVerify) {
                    val verifier = createBuildVerifier()
                    val verifyResult = verifier.verify()
                    logger.lifecycle(verifyResult.message)
                    if (verifyResult.success) {
                        success = true
                        break
                    } else {
                        val msg = "Verification failed (${verifyResult.mode.value}: ${verifyResult.message})"
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

        val durationMs = System.currentTimeMillis() - taskStartMs
        val durationStr = "${durationMs / 1000}s"

        if (success) {
            try {
                System.setProperty("opsx.exec.automated", "true")
                val verifyCommand = TaskLifecycle.resolveVerifyCommand(project)
                val freshItem = TaskParser.parse(tasksFile).flatMap { it.flatten() }
                    .find { it.code == code } ?: taskItem
                TaskLifecycle.onTaskCompleted(
                    project, tasksFile, code, freshItem,
                    skipGate = false, verifyCommand, logger
                )
                AgentLogWriter.complete(logFile, true, durationMs, lastStdout)
                taskLog.success("$code — DONE")
                taskStatusMap[code] = TaskExecStatus(
                    status = TaskExecStatus.DONE,
                    agent = resolvedAgent,
                    startedAt = taskStartedAt,
                    duration = durationStr,
                    level = levelIdx,
                )
            } catch (e: GradleException) {
                AgentLogWriter.complete(logFile, false, durationMs, lastStdout)
                TaskWriter.updateStatus(tasksFile, code, TaskStatus.BLOCKED)
                taskLog.failure("$code — BLOCKED (verification failed: ${e.message})")
                taskStatusMap[code] = TaskExecStatus(
                    status = TaskExecStatus.FAILED,
                    agent = resolvedAgent,
                    startedAt = taskStartedAt,
                    duration = durationStr,
                    error = e.message,
                    level = levelIdx,
                )
                writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
                    verifyModeStr = verifyModeStr)
                throw GradleException("Task '$code' failed verification — chain stopped")
            } finally {
                System.clearProperty("opsx.exec.automated")
            }
        } else {
            AgentLogWriter.complete(logFile, false, durationMs, lastStdout)
            TaskWriter.updateStatus(tasksFile, code, TaskStatus.BLOCKED)
            taskLog.failure("$code — BLOCKED after all retries")
            taskStatusMap[code] = TaskExecStatus(
                status = TaskExecStatus.FAILED,
                agent = resolvedAgent,
                startedAt = taskStartedAt,
                duration = durationStr,
                error = "Failed after $resolvedRetries attempts",
                level = levelIdx,
            )
            writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
                verifyModeStr = verifyModeStr)
            throw GradleException("Task '$code' failed — chain stopped")
        }

        writeExecStatus(statusFile, proposalName, startedAt, levelIdx, taskStatusMap,
            verifyModeStr = verifyModeStr)
    }

    private val statusWriteLock = Any()

    private fun writeExecStatus(
        file: File,
        proposal: String,
        startedAt: String,
        currentLevel: Int,
        tasks: Map<String, TaskExecStatus>,
        isParallel: Boolean = false,
        totalThreads: Int = 1,
        verifyModeStr: String = "build",
    ) {
        val activeCount = tasks.count { it.value.status == TaskExecStatus.RUNNING }
        synchronized(statusWriteLock) {
            ExecStatus.write(file, ExecStatus(
                proposal = proposal,
                startedAt = startedAt,
                currentLevel = currentLevel,
                tasks = tasks,
                parallel = isParallel,
                totalThreads = totalThreads,
                activeThreads = activeCount,
                verifyMode = verifyModeStr,
            ))
        }
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
        val summaryFile = File(outputDir, "$taskId-result.md")
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

    private fun createBuildVerifier(): BuildVerifier {
        val modeStr = when {
            verifyMode.isPresent -> verifyMode.get()
            project.hasProperty("opsx.verify") -> project.property("opsx.verify").toString()
            else -> "build"
        }
        return BuildVerifier(
            projectDir = project.projectDir,
            gradlewPath = resolveGradlew(),
            mode = VerifyMode.fromString(modeStr),
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

    private fun resolveBuildTimeout(): Long {
        return if (project.hasProperty("opsx.buildTimeout"))
            project.property("opsx.buildTimeout").toString().toLong()
        else 10L
    }

    private fun runGradleTask(taskName: String) {
        val proc = ProcessBuilder(
            resolveGradlew(), taskName,
            "-p", project.projectDir.absolutePath,
        )
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()

        val reader = Thread {
            proc.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
        }
        reader.start()

        val timeoutMinutes = resolveBuildTimeout()
        val completed = proc.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        if (!completed) {
            proc.destroyForcibly()
            reader.join(2000)
            throw GradleException("$taskName timed out after ${timeoutMinutes}m")
        }
        reader.join(5000)

        if (proc.exitValue() != 0) {
            throw GradleException("$taskName failed with exit code ${proc.exitValue()}")
        }
    }

    private fun runGradleTaskSafe(taskName: String): Boolean {
        return try {
            val proc = ProcessBuilder(
                resolveGradlew(), taskName,
                "-p", project.projectDir.absolutePath,
            )
                .directory(project.rootDir)
                .redirectErrorStream(true)
                .start()

            val reader = Thread {
                proc.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
            }
            reader.start()

            val timeoutMinutes = resolveBuildTimeout()
            val completed = proc.waitFor(timeoutMinutes, TimeUnit.MINUTES)
            if (!completed) {
                proc.destroyForcibly()
                reader.join(2000)
                return false
            }
            reader.join(5000)
            proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun getLastVerifyOutput(): String {
        val verifyFile = File(project.projectDir, ".opsx/verify.md")
        return if (verifyFile.exists()) verifyFile.readText() else ""
    }

    private fun warnIfTooManyAgents(execDir: File) {
        val runningCount = DashboardReader.scan(execDir).count { it.status == "running" }
        if (runningCount > MAX_RECOMMENDED_AGENTS) {
            logger.warn("\u26a0 $runningCount agents running (recommended max: $MAX_RECOMMENDED_AGENTS)")
        }
    }

    companion object {
        private const val MAX_RECOMMENDED_AGENTS = 10
    }
}
