package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

@org.gradle.api.tasks.UntrackedTask(because = "Reads and displays change status")
abstract class OpenSpecApplyTask : DefaultTask() {

    private var changeName: String = ""

    @Option(option = "name", description = "Change name to apply")
    fun setChangeName(name: String) {
        this.changeName = name
    }

    init {
        group = "opsx"
        description = "[tool] Apply a proposed change. " +
            "Options: --name=<change-name>. " +
            "Use when: Ready to implement a proposal. " +
            "Chain: opsx-status to check progress."
    }

    @TaskAction
    fun apply() {
        val changesRoot = File(project.projectDir, "opsx/changes")

        val name = changeName.ifEmpty {
            autoSelectChange(changesRoot)
        }

        val changeDir = File(changesRoot, name)
        if (!changeDir.exists()) {
            throw org.gradle.api.GradleException("Change '$name' not found at ${changeDir.relativeTo(project.projectDir)}")
        }

        val tasksFile = File(changeDir, "tasks.md")
        if (!tasksFile.exists()) {
            logger.lifecycle("OpenSpec: No tasks.md found for change '$name'. Create artifacts first.")
            return
        }

        val content = tasksFile.readText()
        val total = Regex("- \\[[ x]]").findAll(content).count()
        val done = Regex("- \\[x]").findAll(content).count()
        val remaining = total - done

        logger.lifecycle("OpenSpec: Change '$name'")
        logger.lifecycle("  Progress: $done/$total tasks complete")
        if (remaining > 0) {
            logger.lifecycle("  Remaining: $remaining tasks")
            logger.lifecycle("")
            logger.lifecycle("  Context files:")
            listOf("proposal.md", "design.md", "tasks.md").forEach { f ->
                val file = File(changeDir, f)
                if (file.exists()) logger.lifecycle("    - opsx/changes/$name/$f")
            }
            logger.lifecycle("")
            logger.lifecycle("  Read the context files and implement the remaining tasks.")
        } else {
            logger.lifecycle("  All tasks complete! Run: ./gradlew opsx-archive --name=$name")
        }
    }

    private fun autoSelectChange(changesRoot: File): String {
        if (!changesRoot.exists()) {
            throw org.gradle.api.GradleException("No changes found. Run: ./gradlew opsx-propose --name=my-change")
        }
        val changes = changesRoot.listFiles { f -> f.isDirectory && f.name != "archive" }?.toList() ?: emptyList()
        return when {
            changes.isEmpty() -> throw org.gradle.api.GradleException("No active changes found.")
            changes.size == 1 -> changes[0].name.also { logger.lifecycle("OpenSpec: Auto-selected change '$it'") }
            else -> throw org.gradle.api.GradleException(
                "Multiple changes found: ${changes.joinToString(", ") { it.name }}. Specify with --name=<change>"
            )
        }
    }
}
