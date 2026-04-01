package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import zone.clanker.gradle.generators.AgentCleaner
import zone.clanker.gradle.generators.ToolAdapterRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

@org.gradle.api.tasks.UntrackedTask(because = "Removes files from project directory")
abstract class CleanTask : DefaultTask() {

    @get:Input
    abstract val tools: ListProperty<String>

    init {
        group = OPSX_GROUP
        description = "[tool] Remove all generated OpenSpec files. " +
            "Use when: Switching agents, uninstalling, or cleaning up."
    }

    @TaskAction
    fun clean() {
        var count = 0

        val seenInstrPaths = mutableSetOf<String>()
        // Clean ALL supported tools, not just configured ones
        for (toolId in ToolAdapterRegistry.supportedTools()) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue

            // Deduplicate shared instructions paths (e.g., AGENTS.md used by codex + opencode)
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

        // Remove .opsx/ directory (context.md etc.)
        val opsxDir = File(project.projectDir, ".opsx")
        if (opsxDir.exists()) {
            opsxDir.deleteRecursively()
            logger.lifecycle("OpenSpec: Removed .opsx/ directory")
        }

        // NOTE: opsx/changes/ is intentionally preserved — proposals are committed work.

        logger.lifecycle("OpenSpec: Cleaned $count generated files")
    }
}
