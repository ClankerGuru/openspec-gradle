package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import zone.clanker.gradle.generators.AgentCleaner
import zone.clanker.gradle.generators.ClkxWriter
import zone.clanker.gradle.generators.GeneratedFile
import zone.clanker.gradle.generators.GlobalGitignore
import zone.clanker.gradle.generators.InstructionsGenerator
import zone.clanker.gradle.generators.MarkerAppender
import zone.clanker.gradle.generators.SkillGenerator
import zone.clanker.gradle.generators.SymlinkManager
import zone.clanker.gradle.generators.TaskReconciler
import zone.clanker.gradle.generators.ToolAdapterRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

@UntrackedTask(because = "Generates and installs files to project directory")
abstract class SyncTask : DefaultTask() {

    @get:Input
    abstract val tools: ListProperty<String>

    @get:Input
    abstract val outputDir: Property<File>

    @get:Input
    @get:Optional
    abstract val global: Property<Boolean>

    init {
        group = OPSX_GROUP
        description = "[tool] Agent file generator. Generates skill files for configured AI agents. " +
            "Use when: After config change or plugin upgrade. " +
            "Chain: srcx-context for project metadata."
    }

    @TaskAction
    fun sync() {
        // Ensure generated file patterns are in global gitignore
        GlobalGitignore.ensurePatterns(logger)

        val toolList = tools.get()
        val isGlobal = global.orNull == true

        if (isGlobal) {
            syncGlobal(toolList)
        } else {
            syncProject(toolList)
        }
    }

    /**
     * Global mode: write skills and instructions to ~/.clkx/ only.
     * No per-project files are generated.
     */
    private fun syncGlobal(toolList: List<String>) {
        if (toolList.isEmpty()) {
            logger.lifecycle("OpenSpec: No agents configured (zone.clanker.opsx.agents=none). Cleaning ~/.clkx/.")
            val clkxDir = ClkxWriter.clkxDir()
            if (clkxDir.exists()) {
                clkxDir.deleteRecursively()
                logger.lifecycle("OpenSpec: Removed ~/.clkx/ directory")
            }
            return
        }

        logger.lifecycle("OpenSpec: Global mode — writing skills to ~/.clkx/ for tools: ${toolList.joinToString(", ")}")

        // Load instructions template content for ClkxWriter
        val instructionsContent = (this::class.java.classLoader.getResourceAsStream("templates/instructions.md")
            ?: error("Missing resource: templates/instructions.md — plugin JAR may be corrupted"))
            .use { it.bufferedReader().readText() }

        val count = ClkxWriter.writeAll(toolList, instructionsContent)
        logger.lifecycle("OpenSpec: Written $count skills to ~/.clkx/")
    }

    /**
     * Default project mode: generate .opsx/ context files.
     * If ~/.clkx/ exists, create symlinks for skills/instructions instead of generating per-project.
     * If ~/.clkx/ doesn't exist, fall back to generating per-project files.
     */
    private fun syncProject(toolList: List<String>) {
        val buildDir = outputDir.get()
        buildDir.deleteRecursively()
        buildDir.mkdirs()

        if (toolList.isEmpty()) {
            logger.lifecycle("OpenSpec: No agents configured (zone.clanker.opsx.agents=none). Cleaning generated files.")
            cleanAll()
            return
        }

        val clkxDir = ClkxWriter.clkxDir()
        val useSymlinks = clkxDir.exists() && java.io.File(clkxDir, "skills").exists()

        if (useSymlinks) {
            logger.lifecycle("OpenSpec: Using ~/.clkx/ for skills (symlink mode)")
            // Clean any existing per-project skill files before symlinking
            for (toolId in toolList) {
                val adapter = ToolAdapterRegistry.get(toolId) ?: continue
                AgentCleaner.cleanAgent(project.projectDir, adapter)
            }
            // Create symlinks + marker-append for instructions
            val symlinkResults = SymlinkManager.createSymlinks(project.projectDir, toolList)
            var created = 0; var skipped = 0; var real = 0; var failed = 0
            for ((path, result) in symlinkResults) {
                when (result) {
                    SymlinkManager.LinkResult.CREATED -> created++
                    SymlinkManager.LinkResult.COPIED -> created++
                    SymlinkManager.LinkResult.SKIPPED -> skipped++
                    SymlinkManager.LinkResult.FAILED -> {
                        failed++
                        logger.warn("OpenSpec: Failed to create symlink or copy for: $path")
                    }
                    SymlinkManager.LinkResult.REAL_FILE -> {
                        real++
                        val adapter = toolList.mapNotNull { ToolAdapterRegistry.get(it) }
                            .firstOrNull { it.getInstructionsFilePath() == path }
                        if (adapter != null) {
                            val content = this::class.java.classLoader.getResourceAsStream("templates/instructions.md")
                                ?.use { it.bufferedReader().readText() }
                            if (content != null) {
                                MarkerAppender.append(java.io.File(project.projectDir, path), content)
                            }
                        }
                    }
                }
            }
            logger.lifecycle("OpenSpec: Symlinks — $created created, $skipped up-to-date, $real real files (marker-appended)${if (failed > 0) ", $failed failed" else ""}")

            // Clean up disabled agents — remove symlinks and marker sections for agents not in toolList
            val enabledSet = toolList.toSet()
            val disabledAgents = SymlinkManager.supportedAgents().filter { it !in enabledSet }
            if (disabledAgents.isNotEmpty()) {
                var cleaned = 0
                // Remove symlinks pointing to ~/.clkx/ for disabled agents
                cleaned += SymlinkManager.removeSymlinks(project.projectDir, disabledAgents)
                // Remove marker sections from instruction files for disabled agents
                // and clean any remaining per-project files
                val selectedInstrPaths = toolList.mapNotNull { ToolAdapterRegistry.get(it) }
                    .map { it.getInstructionsFilePath() }.toSet()
                for (agentId in disabledAgents) {
                    val adapter = ToolAdapterRegistry.get(agentId) ?: continue
                    val instrPath = adapter.getInstructionsFilePath()
                    val instrFile = java.io.File(project.projectDir, instrPath)
                    // Only remove markers if the instructions file is not shared with an enabled agent
                    if (instrPath !in selectedInstrPaths && instrFile.exists()) {
                        MarkerAppender.remove(instrFile)
                    }
                    // Clean any remaining per-project skill files
                    val agentCleaned = AgentCleaner.cleanAgent(project.projectDir, adapter)
                    cleaned += agentCleaned
                }
                if (cleaned > 0) {
                    logger.lifecycle("OpenSpec: Cleaned $cleaned files/symlinks from disabled agents: ${disabledAgents.joinToString(", ")}")
                }
            }
            return
        }

        // Fallback: ~/.clkx/ doesn't exist — generate per-project files
        logger.lifecycle("OpenSpec: Generating files for tools: ${toolList.joinToString(", ")} (run opsx-sync -Pglobal=true first for symlink mode)")

        val skills = SkillGenerator.generate(buildDir, toolList)
        val instructionFiles = InstructionsGenerator.generate(buildDir, toolList)
        // Reconcile tasks against symbol index and file paths
        val report = try {
            TaskReconciler.reconcileFull(project.projectDir)
        } catch (e: Exception) {
            logger.debug("OpenSpec: Task reconciliation skipped: ${e.message}")
            null
        }
        val warnings = report?.staleSymbols ?: emptyList()
        if (report != null && report.hasFindings()) {
            logger.lifecycle("")
            logger.lifecycle("OpenSpec: ⚠️ Task reconciliation warnings:")
            for (w in warnings) {
                val suggest = if (w.suggestions.values.flatten().isNotEmpty()) {
                    " → did you mean: ${w.suggestions.values.flatten().joinToString(", ")}?"
                } else ""
                logger.lifecycle("  ${w.taskCode} (${w.proposalName}): references missing symbol(s): ${w.missingSymbols.joinToString(", ")}$suggest")
            }
            for (fw in report.staleFiles) {
                for (path in fw.missingPaths) {
                    val suggest = fw.suggestions[path]?.takeIf { it.isNotEmpty() }
                        ?.let { " → did you mean: ${it.joinToString(", ")}?" } ?: ""
                    logger.lifecycle("  ${fw.taskCode} (${fw.proposalName}): missing file `$path`$suggest")
                }
            }
            logger.lifecycle("")
        }

        // Task-code skills (opsx-T1, opsx-auth-2, etc.) are no longer generated as skill files.
        // TaskCommandGenerator is still used for Gradle task registration, but its skill output is dropped.
        // val taskSkills = TaskCommandGenerator.generate(project.projectDir, buildDir, toolList, warnings)
        val allFiles = skills

        // Install instructions files separately (some need append mode)
        for (file in instructionFiles) {
            val adapter = toolList.mapNotNull { ToolAdapterRegistry.get(it) }
                .firstOrNull { it.getInstructionsFilePath() == file.relativePath }
            if (adapter != null) {
                InstructionsGenerator.install(file, project.projectDir, adapter)
            }
        }

        val totalCount = allFiles.size + instructionFiles.size
        logger.lifecycle("OpenSpec: Generated $totalCount files into ${buildDir.relativeTo(project.projectDir)}")

        // Remove stale generated skills before installing the new set
        removeStaleFiles(allFiles)

        // Install to project root
        installFiles(allFiles)

        logger.lifecycle("OpenSpec: Installed $totalCount files to project root")

        // Clean deselected agents — agents that are supported but not in the current tool list
        val deselected = ToolAdapterRegistry.supportedTools() - toolList.toSet()
        // Don't clean an instructions path that is shared with a selected tool
        val selectedInstrPaths = toolList.mapNotNull { ToolAdapterRegistry.get(it) }
            .map { it.getInstructionsFilePath() }.toSet()
        for (toolId in deselected) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            if (adapter.getInstructionsFilePath() in selectedInstrPaths) {
                // Shared instructions file (e.g., AGENTS.md) — only clean skills, not instructions
                val skillsDir = AgentCleaner.resolveSkillsDir(project.projectDir, adapter)
                if (skillsDir != null && skillsDir.exists() && skillsDir.isDirectory) {
                    skillsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("opsx-") }?.forEach {
                        it.deleteRecursively()
                    }
                    AgentCleaner.pruneEmptyParents(skillsDir, project.projectDir)
                }
            } else {
                val cleaned = AgentCleaner.cleanAgent(project.projectDir, adapter)
                if (cleaned > 0) {
                    logger.lifecycle("OpenSpec: Cleaned $cleaned files from deselected agent: $toolId")
                }
            }
        }

        // Create symlinks from per-project agent config dirs to ~/.clkx/
        val symlinkResults = SymlinkManager.createSymlinks(project.projectDir, toolList)
        var symlinksCreated = 0
        var symlinksSkipped = 0
        var realFiles = 0
        var symlinksFailed = 0
        for ((path, result) in symlinkResults) {
            when (result) {
                SymlinkManager.LinkResult.CREATED -> symlinksCreated++
                SymlinkManager.LinkResult.SKIPPED -> symlinksSkipped++
                SymlinkManager.LinkResult.COPIED -> symlinksCreated++ // treat copy as created
                SymlinkManager.LinkResult.FAILED -> {
                    symlinksFailed++
                    logger.warn("OpenSpec: Failed to create symlink or copy for: $path")
                }
                SymlinkManager.LinkResult.REAL_FILE -> {
                    realFiles++
                    // For real instruction files, append OPSX content via markers
                    val adapter = toolList.mapNotNull { ToolAdapterRegistry.get(it) }
                        .firstOrNull { it.getInstructionsFilePath() == path }
                    if (adapter != null) {
                        val instrFile = instructionFiles.firstOrNull { it.relativePath == path }
                        if (instrFile != null) {
                            val targetFile = File(project.projectDir, path)
                            val content = instrFile.file.readText()
                            MarkerAppender.append(targetFile, content)
                            logger.lifecycle("OpenSpec: Appended OPSX content to existing file: $path")
                        }
                    }
                }
            }
        }
        if (symlinkResults.isNotEmpty()) {
            logger.lifecycle("OpenSpec: Symlinks — $symlinksCreated created, $symlinksSkipped already up-to-date, $realFiles real files (marker-appended)")
        }
    }

    /**
     * Removes previously generated skill files that are no longer in the current output set.
     * Prevents stale artifacts from lingering when templates change.
     */
    private fun removeStaleFiles(currentFiles: List<GeneratedFile>) {
        val currentPaths = currentFiles.map { it.relativePath }.toSet()
        val toolList = tools.get()
        for (toolId in toolList) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            // Check skill directories for stale opsx-prefixed dirs
            val probePath = adapter.getSkillFilePath("__probe__")
            val probeFile = File(project.projectDir, probePath)
            // Skills are at <skillsDir>/<dirName>/SKILL.md — go up two levels to get the skills root
            val skillsDir = probeFile.parentFile?.parentFile
            if (skillsDir != null && skillsDir.exists() && skillsDir.isDirectory) {
                skillsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("opsx-") }?.forEach { dir ->
                    val skillMd = File(dir, "SKILL.md")
                    val skillRel = skillMd.relativeTo(project.projectDir).path
                    if (skillRel !in currentPaths) {
                        dir.deleteRecursively()
                    }
                }
            }
        }
    }

    private fun installFiles(files: List<GeneratedFile>) {
        val projectDir = project.projectDir

        for (generated in files) {
            val target = File(projectDir, generated.relativePath)
            target.parentFile.mkdirs()
            generated.file.copyTo(target, overwrite = true)
        }
    }

    private fun cleanAll() {
        var count = 0
        val seenInstrPaths = mutableSetOf<String>()
        for (toolId in ToolAdapterRegistry.supportedTools()) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            // Deduplicate shared instructions paths
            val instrPath = adapter.getInstructionsFilePath()
            if (instrPath in seenInstrPaths) {
                // Only clean skills for this adapter, not the shared instructions file
                val skillsDir = AgentCleaner.resolveSkillsDir(project.projectDir, adapter)
                if (skillsDir != null && skillsDir.exists() && skillsDir.isDirectory) {
                    skillsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("opsx-") }?.forEach {
                        it.deleteRecursively()
                        count++
                    }
                    AgentCleaner.pruneEmptyParents(skillsDir, project.projectDir)
                }
            } else {
                seenInstrPaths.add(instrPath)
                count += AgentCleaner.cleanAgent(project.projectDir, adapter)
            }
        }
        // Also remove .opsx/ directory
        val opsxDir = File(project.projectDir, ".opsx")
        if (opsxDir.exists()) {
            opsxDir.deleteRecursively()
            logger.lifecycle("OpenSpec: Removed .opsx/ directory")
        }

        logger.lifecycle("OpenSpec: Cleaned $count files")
    }
}
