package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.arch.*
import java.io.File

@CacheableTask
abstract class OpenSpecArchTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val module: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Architecture analyzer. " +
            "Output: .opsx/arch.md. " +
            "Options: -Pmodule=name. " +
            "Use when: You need to understand how components connect, entry points, data flow, and anti-patterns. " +
            "Chain: Read output before making structural changes."
    }

    @TaskAction
    fun analyze() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val root = project.rootProject
        val rootDir = root.projectDir
        val projects = resolveProjects()

        val srcDirs = projects.flatMap { proj ->
            val dirs = mutableListOf<java.io.File>()

            // Java/Kotlin JVM source sets
            val javaExt = proj.extensions.findByType(JavaPluginExtension::class.java)
            javaExt?.sourceSets?.forEach { ss ->
                dirs.addAll(ss.allSource.srcDirs.filter { it.exists() })
            }

            // KMP source sets — discover via the kotlin extension reflectively
            if (dirs.isEmpty()) {
                try {
                    val kotlinExt = proj.extensions.findByName("kotlin")
                    if (kotlinExt != null) {
                        val sourceSets = kotlinExt.javaClass.getMethod("getSourceSets").invoke(kotlinExt)
                        if (sourceSets is Iterable<*>) {
                            for (ss in sourceSets) {
                                if (ss == null) continue
                                val kotlin = ss.javaClass.getMethod("getKotlin").invoke(ss)
                                if (kotlin != null) {
                                    val srcDirSet = kotlin.javaClass.getMethod("getSrcDirs").invoke(kotlin)
                                    if (srcDirSet is Set<*>) {
                                        srcDirSet.filterIsInstance<java.io.File>().filter { it.exists() }.forEach { dirs.add(it) }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback: scan conventional source directories
            if (dirs.isEmpty()) {
                val srcDir = proj.file("src")
                if (srcDir.exists()) {
                    srcDir.listFiles()?.forEach { setDir ->
                        val kotlinDir = java.io.File(setDir, "kotlin")
                        val javaDir = java.io.File(setDir, "java")
                        if (kotlinDir.exists()) dirs.add(kotlinDir)
                        if (javaDir.exists()) dirs.add(javaDir)
                    }
                }
            }

            dirs
        }.distinctBy { it.absolutePath }

        val sources = scanSources(srcDirs).distinctBy { it.file.absolutePath }
        if (sources.isEmpty()) {
            out.writeText("# Architecture Analysis\n\n> No source files found.\n")
            logger.lifecycle("OpenSpec: No sources found for architecture analysis.")
            return
        }

        val components = classifyAll(sources)
        val edges = buildDependencyGraph(components)
        val antiPatterns = detectAntiPatterns(components, edges, rootDir)
        val hubs = findHubClasses(components, edges)

        val sb = StringBuilder()
        sb.appendLine("# Architecture Analysis")
        sb.appendLine()

        // ── Summary ──
        val ktCount = sources.count { it.language == SourceFile.Language.KOTLIN }
        val javaCount = sources.count { it.language == SourceFile.Language.JAVA }
        val basePackage = commonPackagePrefix(sources.map { it.packageName }.filter { it.isNotEmpty() })
        val warnings = antiPatterns.count { it.severity == AntiPattern.Severity.WARNING }
        sb.appendLine("**${sources.size} source files** ($ktCount Kotlin, $javaCount Java) · " +
            "**${edges.size} dependencies** · " +
            "**$warnings warnings**")
        if (basePackage.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Base package: `$basePackage`")
        }
        sb.appendLine()

        // ── Entry Points ──
        val entryPoints = findEntryPoints(components, edges)
        if (entryPoints.isNotEmpty()) {
            sb.appendLine("## Entry Points")
            sb.appendLine()
            for (ep in entryPoints) {
                val relPath = ep.source.file.relativeTo(rootDir).path
                val roleNote = if (ep.role != ComponentRole.OTHER) " (${ep.role.label})" else ""
                val mainNote = if ("main" in ep.source.methods) " — has `main()`" else ""
                sb.appendLine("- `${ep.source.simpleName}`$roleNote$mainNote — `$relPath`")
            }
            sb.appendLine()
        }

        // ── Package Structure ──
        appendPackageStructure(sb, components, rootDir)

        // ── Dependency Diagram ──
        val diagram = generateDependencyDiagram(components, edges)
        if (diagram.isNotEmpty()) {
            sb.appendLine("## Dependency Graph")
            sb.appendLine()
            sb.append(diagram)
            sb.appendLine()
        }

        // ── Sequence Diagrams ──
        val sequences = generateSequenceDiagrams(components, edges)
        if (sequences.isNotEmpty()) {
            sb.appendLine("## Data Flow")
            sb.appendLine()
            sb.append(sequences)
        }

        // ── Hub Classes ──
        if (hubs.isNotEmpty()) {
            sb.appendLine("## Hub Classes")
            sb.appendLine()
            sb.appendLine("Most-depended-on — understand these first.")
            sb.appendLine()
            sb.appendLine("| Class | Dependents | Location |")
            sb.appendLine("|-------|-----------|----------|")
            for ((comp, count) in hubs) {
                val relPath = comp.source.file.relativeTo(rootDir).path
                sb.appendLine("| `${comp.source.simpleName}` | $count | `$relPath` |")
            }
            sb.appendLine()
        }

        // ── Anti-Patterns ──
        if (antiPatterns.isNotEmpty()) {
            sb.appendLine("## Issues & Suggestions")
            sb.appendLine()
            for (ap in antiPatterns) {
                sb.appendLine("- ${ap.severity.icon} ${ap.message}")
                sb.appendLine("  - 📁 `${ap.file.path}`")
                sb.appendLine("  - 💡 ${ap.suggestion}")
            }
            sb.appendLine()
        }

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated architecture analysis at ${out.relativeTo(rootDir)}")
    }

    private fun appendPackageStructure(sb: StringBuilder, components: List<ClassifiedComponent>, rootDir: File) {
        val byGroup = components.groupBy { it.packageGroup }
        if (byGroup.size <= 1) return

        sb.appendLine("## Package Structure")
        sb.appendLine()
        for ((group, comps) in byGroup.entries.sortedBy { it.key }) {
            val files = comps.map { it.source.simpleName }.sorted()
            val smells = comps.filter { it.role in setOf(ComponentRole.MANAGER, ComponentRole.HELPER, ComponentRole.UTIL) }
            val smellNote = if (smells.isNotEmpty()) " ⚠️" else ""
            sb.appendLine("- **$group** (${comps.size} files)$smellNote")
            // Show first few files
            val preview = files.take(8)
            sb.appendLine("  - ${preview.joinToString(", ") { "`$it`" }}${if (files.size > 8) ", …" else ""}")
        }
        sb.appendLine()
    }

    private fun resolveProjects(): List<org.gradle.api.Project> {
        val root = project.rootProject
        return if (module.isPresent) {
            val mod = module.get()
            (root.subprojects + root).filter { it.name == mod || it.path == mod || it.path == ":$mod" }
        } else if (root.subprojects.isNotEmpty()) {
            listOf(root) + root.subprojects.sortedBy { it.path }
        } else {
            listOf(root)
        }
    }
}
