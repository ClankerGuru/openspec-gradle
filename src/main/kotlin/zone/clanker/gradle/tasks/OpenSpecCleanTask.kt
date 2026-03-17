package zone.clanker.gradle.tasks

import zone.clanker.gradle.generators.ToolAdapterRegistry
import zone.clanker.gradle.templates.TemplateRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

@org.gradle.api.tasks.UntrackedTask(because = "Removes files from project directory")
abstract class OpenSpecCleanTask : DefaultTask() {

    @get:Input
    abstract val tools: ListProperty<String>

    init {
        group = "opsx"
        description = "[tool] Remove all generated OpenSpec files. " +
            "Use when: Switching agents, uninstalling, or cleaning up."
    }

    @TaskAction
    fun clean() {
        val toolList = tools.get()
        var count = 0

        for (toolId in toolList) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue

            for (cmd in TemplateRegistry.getCommandTemplates()) {
                val file = File(project.projectDir, adapter.getCommandFilePath(cmd.id))
                if (file.exists()) {
                    file.delete()
                    count++
                }
            }

            for (skill in TemplateRegistry.getSkillTemplates()) {
                val file = File(project.projectDir, adapter.getSkillFilePath(skill.dirName))
                if (file.exists()) {
                    file.delete()
                    val parent = file.parentFile
                    if (parent.exists() && parent.list()?.isEmpty() == true) {
                        parent.delete()
                    }
                    count++
                }
            }
        }

        // Remove .opsx/ directory (context.md etc.)
        val openspecDir = File(project.projectDir, ".opsx")
        if (openspecDir.exists()) {
            openspecDir.deleteRecursively()
            logger.lifecycle("OpenSpec: Removed .opsx/ directory")
        }

        logger.lifecycle("OpenSpec: Cleaned $count generated files")
    }
}
