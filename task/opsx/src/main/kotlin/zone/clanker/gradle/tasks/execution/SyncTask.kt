package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import zone.clanker.gradle.generators.AgentCleaner
import zone.clanker.gradle.generators.GeneratedFile
import zone.clanker.gradle.generators.GlobalGitignore
import zone.clanker.gradle.generators.InstructionsGenerator
import zone.clanker.gradle.generators.SkillGenerator
import zone.clanker.gradle.generators.TaskReconciler
import zone.clanker.gradle.generators.ToolAdapterRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

@UntrackedTask(because = "Generates and installs files to project directory")
abstract class SyncTask : DefaultTask() {

    @get:Input
    abstract val tools: ListProperty<String>

    @get:Input
    abstract val outputDir: Property<File>

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

        val buildDir = outputDir.get()
        buildDir.deleteRecursively()
        buildDir.mkdirs()

        val toolList = tools.get()

        if (toolList.isEmpty()) {
            logger.lifecycle("OpenSpec: No agents configured (zone.clanker.opsx.agents=none). Cleaning generated files.")
            cleanAll()
            return
        }

        logger.lifecycle("OpenSpec: Generating files for tools: ${toolList.joinToString(", ")}")

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
