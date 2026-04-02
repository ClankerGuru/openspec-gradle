package zone.clanker.gradle.tasks.discovery

import zone.clanker.gradle.tasks.SRCX_GROUP

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

@CacheableTask
abstract class DepsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = SRCX_GROUP
        description = "[tool] Dependency resolver. " +
            "Output: .opsx/deps.md. " +
            "Use when: You need full dependency list with GAV coordinates, classified as local vs external. " +
            "Chain: Use with srcx-modules for dependency graph."
    }

    @TaskAction
    fun generate() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val sb = StringBuilder()
        sb.appendLine("# Dependencies")
        sb.appendLine()

        val root = project.rootProject
        val projects = if (root.subprojects.isNotEmpty()) {
            listOf(root) + root.subprojects.sortedBy { it.path }
        } else {
            listOf(root)
        }

        for (proj in projects) {
            appendProjectDeps(sb, proj, root)
        }

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated deps at ${out.relativeTo(root.projectDir)}")
    }

    private fun appendProjectDeps(sb: StringBuilder, proj: org.gradle.api.Project, root: org.gradle.api.Project) {
        val label = if (proj == root && root.subprojects.isEmpty()) "Root" else proj.path

        // Collect module dependencies from declaration configs
        val moduleDeps = mutableSetOf<String>()
        val declaredConfigs = proj.configurations.names.filter { name ->
            val suffixes = listOf("implementation", "api", "compileOnly", "runtimeOnly")
            suffixes.any { name.equals(it, ignoreCase = true) || name.endsWith(it.replaceFirstChar { c -> c.uppercase() }) }
        }.filter { !it.lowercase().contains("test") && !it.lowercase().contains("compilation") }

        for (configName in declaredConfigs) {
            val config = try { proj.configurations.findByName(configName) } catch (_: Exception) { null } ?: continue
            for (dep in config.dependencies) {
                if (dep is ProjectDependency) {
                    moduleDeps.add("- `$configName`: ${dep.path}")
                }
            }
        }

        // Resolve actual dependency tree from resolvable classpath configurations
        val resolvedSections = mutableListOf<Pair<String, List<String>>>()
        val isOffline = proj.gradle.startParameter.isOffline

        if (!isOffline) {
            // Find all resolvable classpath configs (JVM + KMP)
            val classpathConfigs = proj.configurations.names.filter { name ->
                val lower = name.lowercase()
                (lower.endsWith("compileclasspath") || lower.endsWith("runtimeclasspath")) &&
                    !lower.contains("test") &&
                    !lower.contains("metadata")
            }.sorted()

            for (configName in classpathConfigs) {
                val config = try { proj.configurations.findByName(configName) } catch (_: Exception) { null }
                if (config == null || !config.isCanBeResolved) continue
                try {
                    val resolved = config.resolvedConfiguration.firstLevelModuleDependencies
                    if (resolved.isEmpty()) continue
                    val deps = mutableListOf<String>()
                    for (dep in resolved.sortedBy { "${it.moduleGroup}:${it.moduleName}" }) {
                        val gav = "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}"
                        // Collect transitive deps
                        val transitives = dep.children
                            .filter { it.moduleGroup != dep.moduleGroup || it.moduleName != dep.moduleName }
                            .map { "  - ${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}" }
                            .sorted()
                        deps.add("- $gav")
                        deps.addAll(transitives)
                    }
                    resolvedSections.add(configName to deps)
                } catch (e: Exception) {
                    logger.debug("OpenSpec: Failed to resolve $configName for ${proj.path}: ${e.message}")
                }
            }
        }

        // Fall back to declared deps if resolution failed
        if (resolvedSections.isEmpty()) {
            val declared = mutableListOf<String>()
            for (configName in declaredConfigs) {
                val config = try { proj.configurations.findByName(configName) } catch (_: Exception) { null } ?: continue
                for (dep in config.dependencies) {
                    if (dep is ProjectDependency) continue
                    val group = dep.group ?: ""
                    val name = dep.name
                    if (group.isEmpty() && name == "unspecified") continue
                    val version = dep.version ?: ""
                    val gav = if (version.isNotEmpty()) "$group:$name:$version" else "$group:$name"
                    declared.add("- `$configName`: $gav")
                }
            }
            if (declared.isEmpty() && moduleDeps.isEmpty()) return

            sb.appendLine("## $label")
            sb.appendLine()
            if (moduleDeps.isNotEmpty()) {
                sb.appendLine("### Local Module Dependencies")
                sb.appendLine()
                moduleDeps.forEach { sb.appendLine(it) }
                sb.appendLine()
            }
            if (declared.isNotEmpty()) {
                sb.appendLine("### Declared Dependencies (unresolved)")
                sb.appendLine()
                declared.forEach { sb.appendLine(it) }
                sb.appendLine()
            }
            return
        }

        sb.appendLine("## $label")
        sb.appendLine()

        if (moduleDeps.isNotEmpty()) {
            sb.appendLine("### Local Module Dependencies")
            sb.appendLine()
            moduleDeps.forEach { sb.appendLine(it) }
            sb.appendLine()
        }

        for ((configName, deps) in resolvedSections) {
            sb.appendLine("### $configName")
            sb.appendLine()
            deps.forEach { sb.appendLine(it) }
            sb.appendLine()
        }
    }
}
