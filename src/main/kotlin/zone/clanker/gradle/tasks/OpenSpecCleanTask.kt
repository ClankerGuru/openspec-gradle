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
        group = "openspec"
        description = "Removes all generated OpenSpec skill and command files from AI tool directories (.github/, .claude/). Use this to clean up before switching tools or uninstalling the plugin. Does not remove user-created content in openspec/changes/."
    }

    @TaskAction
    fun clean() {
        val toolList = tools.get()
        var count = 0

        for (toolId in toolList) {
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

        logger.lifecycle("OpenSpec: Cleaned $count generated files")
    }

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
}
