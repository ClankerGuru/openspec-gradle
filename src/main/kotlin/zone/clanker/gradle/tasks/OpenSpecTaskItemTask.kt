package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import zone.clanker.gradle.tracking.DependencyGraph
import zone.clanker.gradle.tracking.TaskParser
import zone.clanker.gradle.tracking.TaskItem
import zone.clanker.gradle.tracking.TaskStatus
import zone.clanker.gradle.tracking.TaskWriter
import java.io.File

/**
 * A dynamically registered task for a single task item from a proposal.
 * Allows viewing status or updating it.
 *
 * Registered as: opsx-<code> (e.g., opsx-ttd-1)
 */
@UntrackedTask(because = "Reads and modifies proposal task status in filesystem")
abstract class OpenSpecTaskItemTask : DefaultTask() {

    @get:Input
    abstract val taskCode: Property<String>

    @get:Input
    abstract val proposalName: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "set", description = "Set task status: todo, progress, done, blocked")
    abstract val setStatus: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "run", description = "Execute this task via the exec engine")
    abstract val runTask: Property<String>

    init {
        group = "opsx"
        // Description is set dynamically during registration
    }

    @TaskAction
    fun execute() {
        val code = taskCode.get()
        val name = proposalName.get()
        val tasksFile = File(project.projectDir, "opsx/changes/$name/tasks.md")

        if (!tasksFile.exists()) {
            throw GradleException("tasks.md not found for proposal '$name'")
        }

        // Re-parse fresh to get current status
        val tasks = TaskParser.parse(tasksFile)
        val allFlat = tasks.flatMap { it.flatten() }
        val taskItem = allFlat.find { it.code == code }
            ?: throw GradleException("Task '$code' not found in $name/tasks.md")

        // --run flag: delegate to exec engine
        if (runTask.isPresent) {
            val execTask = project.tasks.findByName("opsx-exec") as? OpenSpecExecTask
                ?: throw GradleException("opsx-exec task not found")
            execTask.taskCodes.set(code)
            execTask.execute()
            return
        }

        if (setStatus.isPresent) {
            val newStatus = when (setStatus.get().lowercase()) {
                "todo", "t" -> TaskStatus.TODO
                "progress", "p", "wip" -> TaskStatus.IN_PROGRESS
                "done", "d", "x" -> TaskStatus.DONE
                "blocked", "b" -> TaskStatus.BLOCKED
                else -> throw GradleException(
                    "Invalid status '${setStatus.get()}'. Use: todo, progress, done, blocked"
                )
            }

            // Check for cycles and deps before marking done or in-progress
            if (newStatus != TaskStatus.TODO) {
                val graph = DependencyGraph(tasks)
                val cycles = graph.findCycles()
                if (cycles.isNotEmpty()) {
                    throw GradleException(
                        "Dependency cycle detected — cannot change status:\n" +
                            cycles.joinToString("\n") { "  ${it.joinToString(" → ")}" } +
                            "\nFix the dependency cycle first."
                    )
                }
            }

            if (newStatus == TaskStatus.DONE || newStatus == TaskStatus.IN_PROGRESS) {
                validateDependencies(code, taskItem, allFlat)
            }

            val updated = TaskWriter.updateStatus(tasksFile, code, newStatus)
            if (!updated) {
                throw GradleException("Failed to update task '$code' in tasks.md")
            }

            // Propagate completion if marking done
            if (newStatus == TaskStatus.DONE) {
                val propagated = TaskWriter.propagateCompletion(tasksFile, TaskParser.parse(tasksFile))
                if (propagated.isNotEmpty()) {
                    logger.lifecycle("Auto-completed parent tasks: ${propagated.joinToString(", ")}")
                }
            }

            val icon = newStatus.icon
            logger.lifecycle("$icon $code → ${newStatus.name}: ${taskItem.description}")
        } else {
            // Print current status
            val icon = taskItem.status.icon
            logger.lifecycle("$icon $code [${taskItem.status.name}]: ${taskItem.description}")
            if (taskItem.children.isNotEmpty()) {
                logger.lifecycle("   Subtasks: ${taskItem.doneCount}/${taskItem.totalCount} done")
            }
            if (taskItem.explicitDeps.isNotEmpty()) {
                logger.lifecycle("   Depends on: ${taskItem.explicitDeps.joinToString(", ")}")
                // Show blocker status
                for (depCode in taskItem.explicitDeps) {
                    val dep = allFlat.find { it.code == depCode }
                    if (dep != null && dep.status != TaskStatus.DONE) {
                        logger.lifecycle("   ⚠️  Blocked by: $depCode (${dep.status.name})")
                    }
                }
            }
        }
    }

    private fun validateDependencies(
        code: String,
        taskItem: TaskItem,
        allTasks: List<TaskItem>
    ) {
        val blockers = mutableListOf<String>()

        for (depCode in taskItem.explicitDeps) {
            val dep = allTasks.find { it.code == depCode }
            if (dep != null && dep.status != TaskStatus.DONE) {
                blockers.add("$depCode (${dep.status.name})")
            }
        }

        if (blockers.isNotEmpty()) {
            throw GradleException(
                "Task '$code' is blocked by incomplete dependencies:\n" +
                    blockers.joinToString("\n") { "  - $it" } +
                    "\nComplete them first, or use --set=todo to reset."
            )
        }
    }
}
