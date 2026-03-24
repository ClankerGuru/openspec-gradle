package zone.clanker.gradle.tasks.workflow

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import zone.clanker.gradle.core.DependencyGraph
import zone.clanker.gradle.core.TaskParser
import zone.clanker.gradle.core.TaskItem
import zone.clanker.gradle.core.TaskStatus
import zone.clanker.gradle.core.TaskWriter
import zone.clanker.gradle.tasks.execution.ExecTask
import java.io.File

/**
 * A dynamically registered task for a single task item from a proposal.
 * Allows viewing status or updating it.
 *
 * Registered as: opsx-<code> (e.g., opsx-ttd-1)
 */
@UntrackedTask(because = "Reads and modifies proposal task status in filesystem")
abstract class TaskItemTask : DefaultTask() {

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

    @get:Input
    @get:Optional
    @get:Option(option = "force", description = "Skip build verification gate when marking done")
    abstract val force: Property<String>

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
            val execTask = project.tasks.findByName("opsx-exec") as? ExecTask
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

            // Validate state transition
            validateTransition(code, taskItem.status, newStatus)

            if (newStatus == TaskStatus.DONE || newStatus == TaskStatus.IN_PROGRESS) {
                validateDependencies(code, taskItem, allFlat)
            }

            // Build gate: verify the build passes before marking DONE
            val skipGate = force.isPresent
            if (newStatus == TaskStatus.DONE && !skipGate) {
                runBuildGate(code)
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

    private fun runBuildGate(code: String) {
        logger.lifecycle("Running build gate for '$code'...")
        val gradlew = resolveGradlew()
        val proc = ProcessBuilder(
            gradlew, "build",
            "--no-daemon", "-p", project.projectDir.absolutePath,
        )
            .directory(project.rootDir)
            .inheritIO()
            .start()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                "Build gate failed for '$code' (exit code $exitCode). " +
                    "Task stays at IN_PROGRESS. Fix the build and try again, " +
                    "or use --force to skip verification."
            )
        }
        logger.lifecycle("Build gate passed for '$code'.")
    }

    private fun resolveGradlew(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
        return File(project.rootDir, wrapperName).absolutePath
    }

    private fun validateTransition(code: String, current: TaskStatus, target: TaskStatus) {
        if (current == target) return // idempotent

        val allowed = when (current) {
            TaskStatus.TODO -> target == TaskStatus.IN_PROGRESS
            TaskStatus.IN_PROGRESS -> target in listOf(TaskStatus.DONE, TaskStatus.BLOCKED, TaskStatus.TODO)
            TaskStatus.DONE -> target == TaskStatus.TODO
            TaskStatus.BLOCKED -> target == TaskStatus.TODO
        }

        if (!allowed) {
            val hint = when {
                current == TaskStatus.TODO && target == TaskStatus.DONE ->
                    "Task must be IN_PROGRESS before marking DONE. Run: --set=progress first."
                current == TaskStatus.TODO && target == TaskStatus.BLOCKED ->
                    "Cannot block a task that hasn't started. Run: --set=progress first."
                current == TaskStatus.DONE && target == TaskStatus.IN_PROGRESS ->
                    "Reset to TODO first. Run: --set=todo then --set=progress."
                current == TaskStatus.BLOCKED && target == TaskStatus.IN_PROGRESS ->
                    "Reset to TODO first. Run: --set=todo then --set=progress."
                current == TaskStatus.BLOCKED && target == TaskStatus.DONE ->
                    "Reset to TODO first, then progress through IN_PROGRESS."
                current == TaskStatus.DONE && target == TaskStatus.BLOCKED ->
                    "Task is already DONE. Reset to TODO first if needed."
                else -> "Invalid transition."
            }
            throw GradleException(
                "Invalid status transition for '$code': ${current.name} → ${target.name}. $hint"
            )
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
