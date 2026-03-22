package zone.clanker.gradle.core

/**
 * A directed graph of task dependencies built from [TaskItem.explicitDeps].
 * Each task code is a node; each dependency is a directed edge from the dependent to its prerequisite.
 */
class DependencyGraph(tasks: List<TaskItem>) {

    private val allTasks: Map<String, TaskItem>
    private val edges: Map<String, Set<String>> // code -> set of dependency codes

    init {
        val flat = tasks.flatMap { it.flatten() }
        allTasks = flat.associateBy { it.code }
        val codeSet = flat.map { it.code }.toSet()
        edges = flat.associate { it.code to it.explicitDeps.filter { dep -> dep in codeSet }.toSet() }
    }

    /** Direct dependencies of [code]. */
    fun dependenciesOf(code: String): Set<String> = edges[code] ?: emptySet()

    /** All transitive dependencies of [code]. */
    fun transitiveDependenciesOf(code: String): Set<String> {
        val result = mutableSetOf<String>()
        val stack = ArrayDeque(dependenciesOf(code))
        while (stack.isNotEmpty()) {
            val dep = stack.removeFirst()
            if (result.add(dep)) {
                stack.addAll(dependenciesOf(dep))
            }
        }
        return result
    }

    /** True if the graph contains at least one cycle. */
    fun hasCycle(): Boolean = findCycles().isNotEmpty()

    /**
     * Find cycles detected during DFS traversal. Returns each cycle as a list
     * of codes forming the cycle path (e.g., [A, B, C, A]).
     * Note: May report duplicate cycles with different starting points.
     */
    fun findCycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            if (node in inStack) {
                // Extract cycle from path
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    cycles.add(path.subList(cycleStart, path.size) + node)
                }
                return
            }
            if (node in visited) return
            visited.add(node)
            inStack.add(node)
            path.add(node)
            for (dep in dependenciesOf(node)) {
                dfs(dep)
            }
            path.removeAt(path.lastIndex)
            inStack.remove(node)
        }

        for (node in allTasks.keys) {
            dfs(node)
        }
        return cycles
    }

    /** True if all dependencies of [code] have status DONE. */
    fun canComplete(code: String): Boolean {
        return dependenciesOf(code).all { dep ->
            allTasks[dep]?.status == TaskStatus.DONE
        }
    }

    /** Tasks whose dependencies are all DONE but which are not DONE themselves. */
    fun unblockedTasks(): List<String> {
        return allTasks.keys.filter { code ->
            allTasks[code]?.status != TaskStatus.DONE && canComplete(code)
        }
    }

    /** Topological order (dependencies before dependents). Throws if cycles exist. */
    fun topologicalOrder(): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val temp = mutableSetOf<String>()

        fun visit(node: String) {
            if (node in temp) throw IllegalStateException("Cycle detected involving $node")
            if (node in visited) return
            temp.add(node)
            for (dep in dependenciesOf(node)) {
                visit(dep)
            }
            temp.remove(node)
            visited.add(node)
            result.add(node)
        }

        for (node in allTasks.keys) {
            visit(node)
        }
        return result
    }
}
