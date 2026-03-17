package zone.clanker.gradle.arch

/**
 * What we can objectively observe about a source file's role.
 * Detected from annotations only — naming is unreliable.
 * When nothing is detected, we just say OTHER and let the graph speak.
 */
enum class ComponentRole(val label: String) {
    // Annotation-detected (high confidence)
    CONTROLLER("Controller"),
    SERVICE("Service"),
    REPOSITORY("Repository"),
    ENTITY("Entity"),
    CONFIGURATION("Configuration"),
    DAO("DAO"),

    // Naming-detected smells (always worth flagging)
    MANAGER("Manager ⚠️"),
    HELPER("Helper ⚠️"),
    UTIL("Util ⚠️"),

    // Everything else — the graph tells the story
    OTHER(""),
}

data class ClassifiedComponent(
    val source: SourceFile,
    val role: ComponentRole,
    /** Top-level package segment relative to base package */
    val packageGroup: String,
)

/**
 * Classify a source file. Conservative — only labels what annotations or clear smells tell us.
 */
fun classifyComponent(source: SourceFile): ClassifiedComponent {
    val role = detectRole(source)
    return ClassifiedComponent(source, role, "")
}

/**
 * Classify all components and compute package groups relative to the common base package.
 */
fun classifyAll(sources: List<SourceFile>): List<ClassifiedComponent> {
    val components = sources.map { classifyComponent(it) }

    val packages = sources.map { it.packageName }.filter { it.isNotEmpty() }
    val basePackage = commonPackagePrefix(packages)

    return components.map { c ->
        val relative = if (basePackage.isNotEmpty()) {
            c.source.packageName.removePrefix(basePackage).removePrefix(".")
        } else {
            c.source.packageName
        }
        val group = relative.split(".").firstOrNull()?.takeIf { it.isNotEmpty() } ?: "(root)"
        c.copy(packageGroup = group)
    }
}

/**
 * Find the longest common package prefix across all packages.
 */
fun commonPackagePrefix(packages: List<String>): String {
    if (packages.isEmpty()) return ""
    val segments = packages.map { it.split(".") }
    val minLen = segments.minOf { it.size }
    val common = mutableListOf<String>()
    for (i in 0 until minLen) {
        val seg = segments[0][i]
        if (segments.all { it[i] == seg }) common.add(seg) else break
    }
    return common.joinToString(".")
}

/**
 * Identify entry points — things that look like they start a flow.
 * Detected from: main() functions, annotated endpoints, or root nodes in the graph.
 */
fun findEntryPoints(
    components: List<ClassifiedComponent>,
    edges: List<ClassDependency> = emptyList(),
): List<ClassifiedComponent> {
    // 1. Classes with main() method
    val withMain = components.filter { "main" in it.source.methods }
    if (withMain.isNotEmpty()) return withMain

    // 2. Annotated entry points (controllers, etc.)
    val annotated = components.filter { it.role == ComponentRole.CONTROLLER }
    if (annotated.isNotEmpty()) return annotated

    // 3. Root nodes — components that have outgoing edges but no incoming edges
    if (edges.isNotEmpty()) {
        val hasInbound = edges.map { it.to.source.qualifiedName }.toSet()
        val hasOutbound = edges.map { it.from.source.qualifiedName }.toSet()
        val roots = components.filter {
            it.source.qualifiedName in hasOutbound && it.source.qualifiedName !in hasInbound
        }
        if (roots.isNotEmpty()) return roots
    }

    return emptyList()
}

private fun detectRole(s: SourceFile): ComponentRole {
    val name = s.simpleName
    val annos = s.annotations.map { it.substringAfterLast(".") }.toSet()

    // Annotation-based only (high confidence)
    if ("RestController" in annos || "Controller" in annos || "RequestMapping" in annos) return ComponentRole.CONTROLLER
    if ("Service" in annos) return ComponentRole.SERVICE
    if ("Repository" in annos) return ComponentRole.REPOSITORY
    if ("Entity" in annos || "Table" in annos) return ComponentRole.ENTITY
    if ("Configuration" in annos || "SpringBootApplication" in annos) return ComponentRole.CONFIGURATION
    if ("Dao" in annos || "Database" in annos) return ComponentRole.DAO

    // Naming-based smells only (always worth calling out)
    return when {
        name.endsWith("Manager") -> ComponentRole.MANAGER
        name.endsWith("Helper") -> ComponentRole.HELPER
        name.endsWith("Util") || name.endsWith("Utils") || name.endsWith("Utility") -> ComponentRole.UTIL
        else -> ComponentRole.OTHER
    }
}
