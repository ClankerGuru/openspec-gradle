package zone.clanker.gradle.exec

import zone.clanker.gradle.core.DependencyGraph
import zone.clanker.gradle.core.TaskItem
import zone.clanker.gradle.core.TaskStatus

/**
 * Groups tasks into execution levels for parallel scheduling.
 *
 * Level 0 = tasks with no pending dependencies.
 * Level N = tasks whose dependencies are all in levels < N.
 * Tasks already DONE are skipped.
 *
 * Uses Kahn's algorithm (BFS topological sort) to assign levels.
 */
class LevelScheduler(private val tasks: List<TaskItem>) {

    /**
     * Returns tasks grouped by execution level.
     * Each level contains tasks that can run in parallel.
     * Levels must be executed sequentially.
     *
     * @throws IllegalStateException if cycles are detected
     */
    fun schedule(): List<List<TaskItem>> {
        val graph = DependencyGraph(tasks)
        if (graph.hasCycle()) {
            throw IllegalStateException("Cycle detected in task dependencies")
        }

        val flat = tasks.flatMap { it.flatten() }
        val pending = flat.filter { it.status != TaskStatus.DONE }
        if (pending.isEmpty()) return emptyList()

        val doneSet = flat.filter { it.status == TaskStatus.DONE }.map { it.code }.toMutableSet()
        val remaining = pending.associateBy { it.code }.toMutableMap()
        val levels = mutableListOf<List<TaskItem>>()

        while (remaining.isNotEmpty()) {
            // Find tasks whose dependencies are all done
            val ready = remaining.values.filter { task ->
                task.explicitDeps.all { dep -> dep in doneSet }
            }

            if (ready.isEmpty()) {
                throw IllegalStateException(
                    "Deadlock: no tasks can proceed. Remaining: ${remaining.keys}"
                )
            }

            levels.add(ready)
            for (task in ready) {
                remaining.remove(task.code)
                doneSet.add(task.code)
            }
        }

        return levels
    }
}
