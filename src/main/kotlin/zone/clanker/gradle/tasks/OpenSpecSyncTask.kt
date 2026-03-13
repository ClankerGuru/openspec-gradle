package zone.clanker.gradle.tasks

import zone.clanker.gradle.generators.CommandGenerator
import zone.clanker.gradle.generators.GeneratedFile
import zone.clanker.gradle.generators.GlobalGitignore
import zone.clanker.gradle.generators.SkillGenerator
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
        group = "openspec"
        description = "Generates Markdown skill and command files for configured AI coding assistants (e.g. Claude Code, GitHub Copilot) and installs them into their tool-specific directories. Updates prompts to match the current plugin version. Run this after changing the openspec {} configuration or upgrading the plugin."
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

        // Clean files from tools that are no longer active
        val removedCount = cleanInactiveTools(toolList)
        if (removedCount > 0) {
            logger.lifecycle("OpenSpec: Removed $removedCount files from inactive tools")
        }

        logger.lifecycle("OpenSpec: Generating files for tools: ${toolList.joinToString(", ")}")

        val skills = SkillGenerator.generate(buildDir, toolList)
        val commands = CommandGenerator.generate(buildDir, toolList)
        val allFiles = skills + commands

        logger.lifecycle("OpenSpec: Generated ${allFiles.size} files into ${buildDir.relativeTo(project.projectDir)}")

        // Install to project root
        installFiles(allFiles)

        logger.lifecycle("OpenSpec: Installed ${allFiles.size} files to project root")
    }

    private fun installFiles(files: List<GeneratedFile>) {
        val projectDir = project.projectDir

        for (generated in files) {
            val target = File(projectDir, generated.relativePath)
            target.parentFile.mkdirs()
            generated.file.copyTo(target, overwrite = true)
        }
    }

    /**
     * Removes generated files for tools that are NOT in the active list.
     * Also cleans up empty parent directories left behind.
     */
    private fun cleanInactiveTools(activeTools: List<String>): Int {
        val activeSet = activeTools.toSet()
        val inactiveTools = ToolAdapterRegistry.supportedTools().filter { it !in activeSet }
        var count = 0
        for (toolId in inactiveTools) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            for (cmd in TemplateRegistry.getCommandTemplates()) {
                val file = File(project.projectDir, adapter.getCommandFilePath(cmd.id))
                if (file.exists()) { file.delete(); count++ }
                pruneEmptyParents(file.parentFile)
            }
            for (skill in TemplateRegistry.getSkillTemplates()) {
                val file = File(project.projectDir, adapter.getSkillFilePath(skill.dirName))
                if (file.exists()) { file.delete(); count++ }
                pruneEmptyParents(file.parentFile)
            }
        }
        return count
    }

    /**
     * Removes empty directories up to (but not including) the project dir.
     */
    private fun pruneEmptyParents(dir: File?) {
        var current = dir
        val projectDir = project.projectDir
        while (current != null && current != projectDir && current.startsWith(projectDir)) {
            if (current.exists() && current.isDirectory && current.list()?.isEmpty() == true) {
                current.delete()
                current = current.parentFile
            } else {
                break
            }
        }
    }

    private fun cleanAll() {
        var count = 0
        for (toolId in ToolAdapterRegistry.supportedTools()) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            for (cmd in TemplateRegistry.getCommandTemplates()) {
                val file = File(project.projectDir, adapter.getCommandFilePath(cmd.id))
                if (file.exists()) { file.delete(); count++ }
            }
            for (skill in TemplateRegistry.getSkillTemplates()) {
                val file = File(project.projectDir, adapter.getSkillFilePath(skill.dirName))
                if (file.exists()) { file.delete(); count++ }
                pruneEmptyParents(file.parentFile)
            }
        }
        logger.lifecycle("OpenSpec: Cleaned $count files")
    }
}
