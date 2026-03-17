package zone.clanker.gradle.arch

import java.io.File

data class AntiPattern(
    val severity: Severity,
    val message: String,
    val file: File,
    val suggestion: String,
) {
    enum class Severity(val icon: String) {
        WARNING("⚠️"),
        INFO("💡"),
    }
}

/**
 * Detect anti-patterns across the classified components.
 */
fun detectAntiPatterns(
    components: List<ClassifiedComponent>,
    edges: List<ClassDependency>,
    rootDir: File,
): List<AntiPattern> {
    val patterns = mutableListOf<AntiPattern>()

    patterns.addAll(detectSmellClasses(components, rootDir))
    patterns.addAll(detectSingleImplInterfaces(components, rootDir))
    patterns.addAll(detectGodClasses(components, rootDir))
    patterns.addAll(detectDeepInheritance(components, rootDir))
    patterns.addAll(detectCircularDeps(components, edges))
    patterns.addAll(detectMissingTests(components, rootDir))

    return patterns.sortedWith(compareBy({ it.severity }, { it.file.path }))
}

/**
 * Manager, Helper, Util classes — behavior probably belongs somewhere specific.
 */
private fun detectSmellClasses(components: List<ClassifiedComponent>, rootDir: File): List<AntiPattern> =
    components.filter { it.role in setOf(ComponentRole.MANAGER, ComponentRole.HELPER, ComponentRole.UTIL) }
        .map { c ->
            val roleLabel = c.role.name.lowercase()
            AntiPattern(
                severity = AntiPattern.Severity.WARNING,
                message = "`${c.source.simpleName}` is a ${roleLabel} class",
                file = c.source.file.relativeTo(rootDir),
                suggestion = "Behavior in ${roleLabel} classes usually belongs in a specific class closer to where it's used. " +
                    "Consider moving methods to the classes that actually need them."
            )
        }

/**
 * Interfaces with only one implementation — premature abstraction.
 */
private fun detectSingleImplInterfaces(components: List<ClassifiedComponent>, rootDir: File): List<AntiPattern> {
    val interfaces = components.filter { it.source.isInterface }
    val allSupertypes = components.flatMap { c -> c.source.supertypes.map { it to c } }
        .groupBy({ it.first }, { it.second })

    return interfaces.mapNotNull { iface ->
        val impls = allSupertypes[iface.source.simpleName] ?: emptyList()
        if (impls.size == 1) {
            val impl = impls[0]
            AntiPattern(
                severity = AntiPattern.Severity.INFO,
                message = "Interface `${iface.source.simpleName}` has only one implementation: `${impl.source.simpleName}`",
                file = iface.source.file.relativeTo(rootDir),
                suggestion = "If this interface isn't meant for testing or future extension, " +
                    "consider using `${impl.source.simpleName}` directly. Prefer concrete classes over premature abstractions."
            )
        } else null
    }
}

/**
 * God classes — too many imports or methods, doing too much.
 */
private fun detectGodClasses(components: List<ClassifiedComponent>, rootDir: File): List<AntiPattern> =
    components
        .filter { it.source.imports.size > 30 || it.source.methods.size > 25 || it.source.lineCount > 500 }
        .filter { it.role != ComponentRole.CONFIGURATION }
        .map { c ->
            val reasons = mutableListOf<String>()
            if (c.source.imports.size > 30) reasons.add("${c.source.imports.size} imports")
            if (c.source.methods.size > 25) reasons.add("${c.source.methods.size} methods")
            if (c.source.lineCount > 500) reasons.add("${c.source.lineCount} lines")
            AntiPattern(
                severity = AntiPattern.Severity.WARNING,
                message = "`${c.source.simpleName}` may be doing too much (${reasons.joinToString(", ")})",
                file = c.source.file.relativeTo(rootDir),
                suggestion = "Consider splitting into smaller, focused classes. " +
                    "Each class should have a single responsibility."
            )
        }

/**
 * Deep inheritance chains — prefer composition.
 */
private fun detectDeepInheritance(components: List<ClassifiedComponent>, rootDir: File): List<AntiPattern> {
    val bySimpleName = components.associateBy { it.source.simpleName }

    fun depth(c: ClassifiedComponent, visited: Set<String> = emptySet()): Int {
        if (c.source.simpleName in visited) return 0
        val parent = c.source.supertypes.firstOrNull()?.let { bySimpleName[it] } ?: return 0
        if (parent.source.isInterface) return 0
        return 1 + depth(parent, visited + c.source.simpleName)
    }

    return components
        .filter { !it.source.isInterface }
        .mapNotNull { c ->
            val d = depth(c)
            if (d >= 3) {
                val chain = buildChain(c, bySimpleName).joinToString(" → ")
                AntiPattern(
                    severity = AntiPattern.Severity.WARNING,
                    message = "`${c.source.simpleName}` has inheritance depth $d: $chain",
                    file = c.source.file.relativeTo(rootDir),
                    suggestion = "Deep inheritance makes code rigid. Prefer composition — " +
                        "extract shared behavior into separate classes and compose them."
                )
            } else null
        }
}

private fun buildChain(c: ClassifiedComponent, byName: Map<String, ClassifiedComponent>): List<String> {
    val chain = mutableListOf(c.source.simpleName)
    var current = c
    val visited = mutableSetOf(c.source.simpleName)
    while (true) {
        val parent = current.source.supertypes.firstOrNull()?.let { byName[it] } ?: break
        if (parent.source.isInterface || parent.source.simpleName in visited) break
        chain.add(parent.source.simpleName)
        visited.add(parent.source.simpleName)
        current = parent
    }
    return chain
}

/**
 * Circular dependencies between components.
 */
private fun detectCircularDeps(components: List<ClassifiedComponent>, edges: List<ClassDependency>): List<AntiPattern> {
    val cycles = findCycles(components, edges)
    return cycles.take(10).map { cycle ->
        AntiPattern(
            severity = AntiPattern.Severity.WARNING,
            message = "Circular dependency: ${cycle.joinToString(" → ")}",
            file = File("."),
            suggestion = "Break the cycle by extracting a shared interface or moving shared logic to a separate class."
        )
    }
}

/**
 * Source files with no corresponding test file.
 */
private fun detectMissingTests(components: List<ClassifiedComponent>, rootDir: File): List<AntiPattern> {
    val testNames = components
        .filter { it.source.file.path.contains("/test/") || it.source.file.path.contains("\\test\\") }
        .map { it.source.simpleName.removeSuffix("Test").removeSuffix("Spec") }
        .toSet()

    val mainComponents = components
        .filter { !it.source.file.path.contains("/test/") && !it.source.file.path.contains("\\test\\") }
        .filter { it.role != ComponentRole.OTHER && it.role != ComponentRole.CONFIGURATION && it.role != ComponentRole.ENTITY }
        .filter { !it.source.isInterface && !it.source.isDataClass }

    val untested = mainComponents.filter { it.source.simpleName !in testNames }

    return if (untested.size > 10) {
        listOf(AntiPattern(
            severity = AntiPattern.Severity.INFO,
            message = "${untested.size} classes have no corresponding test file",
            file = File("."),
            suggestion = "Consider adding tests for key components, especially: " +
                untested.take(5).joinToString(", ") { "`${it.source.simpleName}`" }
        ))
    } else {
        untested.map { c ->
            AntiPattern(
                severity = AntiPattern.Severity.INFO,
                message = "`${c.source.simpleName}` has no test",
                file = c.source.file.relativeTo(rootDir),
                suggestion = "Consider adding `${c.source.simpleName}Test`."
            )
        }
    }
}
