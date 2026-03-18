package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery

@CacheableTask
abstract class OpenSpecModulesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Module graph generator. " +
            "Output: .opsx/modules.md. " +
            "Use when: You need to understand multi-project structure and inter-module dependencies. " +
            "Chain: Use with opsx-deps for full dependency details."
    }

    @TaskAction
    fun generate() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val sb = StringBuilder()
        val root = project.rootProject
        val subs = root.subprojects.sortedBy { it.path }

        if (subs.isEmpty()) {
            sb.appendLine("# Modules")
            sb.appendLine()
            sb.appendLine("Single-module project: `${root.name}`")
            sb.appendLine()
            appendModuleDetails(sb, root, root)
            out.writeText(sb.toString())
            logger.lifecycle("OpenSpec: Generated modules at ${out.relativeTo(root.projectDir)}")
            return
        }

        // Build adjacency: project path -> list of project paths it depends on
        val edges = mutableMapOf<String, MutableList<String>>()
        for (sub in subs) {
            val deps = mutableListOf<String>()
            sub.configurations.forEach { config ->
                config.dependencies.filterIsInstance<ProjectDependency>().forEach { dep ->
                    // Skip self-references
                    if (dep.path != sub.path) {
                        deps.add(dep.path)
                    }
                }
            }
            edges[sub.path] = deps.distinct().sorted().toMutableList()
        }

        sb.appendLine("# Modules (${subs.size})")
        sb.appendLine()

        // Mermaid dependency graph
        sb.appendLine("```mermaid")
        sb.appendLine("flowchart TD")
        for (sub in subs) {
            val id = sanitizeId(sub.path)
            val label = sub.path.removePrefix(":")
            sb.appendLine("    $id[\"$label\"]")
        }
        for (sub in subs) {
            val fromId = sanitizeId(sub.path)
            val deps = edges[sub.path] ?: emptyList()
            for (dep in deps) {
                val toId = sanitizeId(dep)
                sb.appendLine("    $fromId --> $toId")
            }
        }
        sb.appendLine("```")
        sb.appendLine()

        // Module details — the useful part
        for (sub in subs) {
            appendModuleDetails(sb, sub, root)
            val deps = edges[sub.path] ?: emptyList()
            if (deps.isNotEmpty()) {
                sb.appendLine("**Depends on:** ${deps.joinToString(", ") { "`$it`" }}")
                sb.appendLine()
            }
            // Who depends on this module
            val dependents = subs.filter { other ->
                (edges[other.path] ?: emptyList()).contains(sub.path)
            }.map { it.path }
            if (dependents.isNotEmpty()) {
                sb.appendLine("**Used by:** ${dependents.joinToString(", ") { "`$it`" }}")
                sb.appendLine()
            }
        }

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated modules at ${out.relativeTo(root.projectDir)}")
    }

    private fun appendModuleDetails(sb: StringBuilder, proj: org.gradle.api.Project, root: org.gradle.api.Project) {
        val label = if (proj == root && root.subprojects.isEmpty()) proj.name else proj.path
        sb.appendLine("## `$label`")
        sb.appendLine()

        val details = mutableListOf<String>()

        // Source file count
        val srcDirs = SourceDiscovery.discoverSourceDirs(proj)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)
        val ktCount = sourceFiles.count { it.extension == "kt" }
        val javaCount = sourceFiles.count { it.extension == "java" }
        if (sourceFiles.isNotEmpty()) {
            val parts = mutableListOf<String>()
            if (ktCount > 0) parts.add("$ktCount Kotlin")
            if (javaCount > 0) parts.add("$javaCount Java")
            details.add("**Sources:** ${sourceFiles.size} files (${parts.joinToString(", ")})")
        }

        // Detected plugins/frameworks
        val plugins = mutableListOf<String>()
        proj.plugins.forEach { plugin ->
            val name = plugin.javaClass.name.lowercase()
            when {
                name.contains("android") && name.contains("application") -> plugins.add("Android App")
                name.contains("android") && name.contains("library") -> plugins.add("Android Library")
                name.contains("multiplatform") -> plugins.add("Kotlin Multiplatform")
                name.contains("compose") -> plugins.add("Compose")
                name.contains("spring") -> plugins.add("Spring")
                name.contains("serialization") -> plugins.add("kotlinx.serialization")
                name.contains("jvm") && name.contains("kotlin") -> plugins.add("Kotlin/JVM")
            }
        }
        // Deduplicate
        val uniquePlugins = plugins.distinct()
        if (uniquePlugins.isNotEmpty()) {
            details.add("**Type:** ${uniquePlugins.joinToString(", ")}")
        }

        // External dependency count (non-project deps)
        var extDepCount = 0
        proj.configurations.forEach { config ->
            if (config.name in setOf("implementation", "api", "compileOnly", "runtimeOnly",
                    "commonMainImplementation", "commonMainApi")) {
                config.dependencies.forEach { dep ->
                    if (dep !is ProjectDependency) extDepCount++
                }
            }
        }
        if (extDepCount > 0) {
            details.add("**External deps:** $extDepCount")
        }

        // Source directories
        if (srcDirs.isNotEmpty()) {
            val relDirs = srcDirs.map { it.relativeTo(root.projectDir).path }
            details.add("**Source roots:** ${relDirs.joinToString(", ") { "`$it`" }}")
        }

        for (detail in details) {
            sb.appendLine(detail)
        }
        if (details.isNotEmpty()) sb.appendLine()
    }

    private fun sanitizeId(path: String): String =
        path.replace(Regex("[^a-zA-Z0-9_]"), "_").trimStart('_')
}
