package zone.clanker.gradle.tasks

import zone.clanker.gradle.generators.CommandGenerator
import zone.clanker.gradle.generators.GeneratedFile
import zone.clanker.gradle.generators.GlobalGitignore
import zone.clanker.gradle.generators.InstructionsGenerator
import zone.clanker.gradle.generators.SkillGenerator
import zone.clanker.gradle.generators.TaskCommandGenerator
import zone.clanker.gradle.generators.TaskReconciler
import zone.clanker.gradle.generators.ToolAdapterRegistry
import zone.clanker.gradle.templates.TemplateRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

@UntrackedTask(because = "Generates and installs files to project directory")
abstract class OpenSpecSyncTask : DefaultTask() {

    @get:Input
    abstract val tools: ListProperty<String>

    @get:Input
    abstract val outputDir: Property<File>

    init {
        group = "opsx"
        description = "[tool] Agent file generator. Generates skill/command files for configured AI agents. " +
            "Use when: After config change or plugin upgrade. " +
            "Chain: opsx-context for project metadata."
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
            logger.lifecycle("OpenSpec: No agents configured (zone.clanker.openspec.agents=none). Cleaning generated files.")
            cleanAll()
            return
        }

        logger.lifecycle("OpenSpec: Generating files for tools: ${toolList.joinToString(", ")}")

        val skills = SkillGenerator.generate(buildDir, toolList)
        val commands = CommandGenerator.generate(buildDir, toolList)
        val instructionFiles = InstructionsGenerator.generate(buildDir, toolList)
        // Reconcile tasks against symbol index
        val warnings = try {
            TaskReconciler.reconcile(project.projectDir)
        } catch (e: Exception) {
            logger.debug("OpenSpec: Task reconciliation skipped: ${e.message}")
            emptyList()
        }
        if (warnings.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("OpenSpec: ⚠️ Task reconciliation warnings:")
            for (w in warnings) {
                val suggest = if (w.suggestions.values.flatten().isNotEmpty()) {
                    " → did you mean: ${w.suggestions.values.flatten().joinToString(", ")}?"
                } else ""
                logger.lifecycle("  ${w.taskCode} (${w.proposalName}): references missing symbol(s): ${w.missingSymbols.joinToString(", ")}$suggest")
            }
            logger.lifecycle("")
        }

        val taskCommands = TaskCommandGenerator.generate(project.projectDir, buildDir, toolList, warnings)
        val allFiles = skills + commands + taskCommands

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

        // Remove stale generated artifacts before installing the new set
        removeStaleFiles(allFiles)

        // Install to project root
        installFiles(allFiles)

        logger.lifecycle("OpenSpec: Installed $totalCount files to project root")
    }

    /**
     * Removes previously generated files that are no longer in the current output set.
     * Prevents stale artifacts from lingering when agents are removed or templates change.
     */
    private fun removeStaleFiles(currentFiles: List<GeneratedFile>) {
        val currentPaths = currentFiles.map { it.relativePath }.toSet()
        val toolList = tools.get()
        for (toolId in toolList) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            // Check command directories for stale opsx-prefixed files
            val probePath = adapter.getCommandFilePath("__probe__")
            val probeFile = File(project.projectDir, probePath)
            val commandDir = probeFile.parentFile
            if (commandDir != null && commandDir.exists() && commandDir.isDirectory) {
                commandDir.listFiles()?.filter { it.name.startsWith("opsx-") }?.forEach { file ->
                    val rel = file.relativeTo(project.projectDir).path
                    if (file.isDirectory) {
                        // For Codex-style skill dirs, check if the SKILL.md inside is in the current set
                        val skillMd = File(file, "SKILL.md")
                        val skillRel = skillMd.relativeTo(project.projectDir).path
                        if (skillRel !in currentPaths) {
                            file.deleteRecursively()
                        }
                    } else if (rel !in currentPaths) {
                        file.delete()
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
        for (toolId in ToolAdapterRegistry.supportedTools()) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            // Clean instructions file (handles append-mode files with markers)
            if (InstructionsGenerator.clean(project.projectDir, adapter)) count++
            // Clean all command files (static + dynamic task commands)
            val probePath = adapter.getCommandFilePath("__probe__")
            val probeFile = File(project.projectDir, probePath)
            val commandDir = probeFile.parentFile
            if (commandDir != null && commandDir.exists() && commandDir.isDirectory) {
                // If the command dir is opsx-specific (e.g. .claude/commands/opsx/), clean it entirely
                // Otherwise clean opsx-prefixed files and directories (e.g. .agents/skills/opsx-find/)
                if (commandDir.name == "opsx") {
                    commandDir.deleteRecursively()
                    count++
                } else {
                    commandDir.listFiles()?.filter { it.name.startsWith("opsx-") }?.forEach {
                        if (it.isDirectory) it.deleteRecursively() else it.delete()
                        count++
                    }
                }
            }
            // Clean skills
            for (skill in TemplateRegistry.getSkillTemplates()) {
                val file = File(project.projectDir, adapter.getSkillFilePath(skill.dirName))
                if (file.exists()) {
                    file.delete()
                    val parent = file.parentFile
                    if (parent.exists() && parent.list()?.isEmpty() == true) parent.delete()
                    count++
                }
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
