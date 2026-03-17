package zone.clanker.gradle.arch

/**
 * An edge in the class dependency graph.
 */
data class ClassDependency(
    val from: ClassifiedComponent,
    val to: ClassifiedComponent,
)

/**
 * Build a dependency graph between classified components using import analysis.
 * Only includes edges between project source files (ignores external imports).
 */
fun buildDependencyGraph(components: List<ClassifiedComponent>): List<ClassDependency> {
    // Index: simple name → component(s), qualified name → component
    val bySimpleName = components.groupBy { it.source.simpleName }
    val byQualifiedName = components.associateBy { it.source.qualifiedName }

    val edges = mutableListOf<ClassDependency>()

    for (component in components) {
        for (imp in component.source.imports) {
            // Try qualified match first
            val target = byQualifiedName[imp]
            if (target != null && target !== component) {
                edges.add(ClassDependency(component, target))
                continue
            }

            // Try simple name match from the import's last segment
            val simpleName = imp.substringAfterLast(".")
            val candidates = bySimpleName[simpleName] ?: continue
            for (candidate in candidates) {
                if (candidate !== component && candidate.source.qualifiedName == imp) {
                    edges.add(ClassDependency(component, candidate))
                }
            }
        }

        // Also check supertypes by simple name
        for (supertype in component.source.supertypes) {
            val candidates = bySimpleName[supertype] ?: continue
            for (candidate in candidates) {
                if (candidate !== component) {
                    edges.add(ClassDependency(component, candidate))
                }
            }
        }
    }

    return edges.distinct()
}

/**
 * Find hub classes — the most-depended-on components.
 * Returns components sorted by inbound edge count (descending).
 */
fun findHubClasses(components: List<ClassifiedComponent>, edges: List<ClassDependency>, limit: Int = 15): List<Pair<ClassifiedComponent, Int>> {
    val inboundCounts = mutableMapOf<String, Int>()
    for (edge in edges) {
        val key = edge.to.source.qualifiedName
        inboundCounts[key] = (inboundCounts[key] ?: 0) + 1
    }

    val componentByName = components.associateBy { it.source.qualifiedName }

    return inboundCounts.entries
        .sortedByDescending { it.value }
        .take(limit)
        .mapNotNull { (name, count) -> componentByName[name]?.let { it to count } }
}

/**
 * Detect circular dependencies between components.
 * Returns lists of component names forming cycles.
 */
fun findCycles(components: List<ClassifiedComponent>, edges: List<ClassDependency>): List<List<String>> {
    val adjacency = mutableMapOf<String, MutableSet<String>>()
    for (edge in edges) {
        adjacency.getOrPut(edge.from.source.qualifiedName) { mutableSetOf() }
            .add(edge.to.source.qualifiedName)
    }

    val cycles = mutableListOf<List<String>>()
    val visited = mutableSetOf<String>()
    val inStack = mutableSetOf<String>()
    val stack = mutableListOf<String>()

    fun dfs(node: String) {
        if (node in inStack) {
            // Found a cycle — extract it
            val cycleStart = stack.indexOf(node)
            if (cycleStart >= 0) {
                val cycle = stack.subList(cycleStart, stack.size).map { it.substringAfterLast(".") } + node.substringAfterLast(".")
                cycles.add(cycle)
            }
            return
        }
        if (node in visited) return

        visited.add(node)
        inStack.add(node)
        stack.add(node)

        for (neighbor in adjacency[node] ?: emptySet()) {
            dfs(neighbor)
        }

        stack.removeAt(stack.size - 1)
        inStack.remove(node)
    }

    for (node in adjacency.keys) {
        dfs(node)
    }

    return cycles
}
