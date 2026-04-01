package zone.clanker.gradle.tasks.intelligence

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import java.io.File

@UntrackedTask(because = "Find-usages reads all project sources dynamically")
abstract class FindTask : DefaultTask() {

    @get:Input
    abstract val symbol: Property<String>

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Find all usages of a symbol across the codebase. " +
            "Output: .opsx/find.md. " +
            "Options: -Psymbol=Name (required). " +
            "Use when: You need to know everywhere a class/function/property is used. " +
            "Chain: Use before srcx-rename for impact analysis."
    }

    @TaskAction
    fun find() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir
        val name = symbol.get()

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Find: `$name`\n\n> No source files found.\n")
            logger.lifecycle("OpenSpec: No sources found.")
            return
        }

        logger.lifecycle("OpenSpec: Searching for `$name` in ${sourceFiles.size} files...")
        val index = SymbolIndex.build(sourceFiles)
        val results = index.findUsagesByName(name)

        val sb = StringBuilder()
        sb.appendLine("# Find: `$name`")
        sb.appendLine()

        if (results.isEmpty()) {
            sb.appendLine("> No symbol found matching `$name`.")
        } else {
            for ((sym, usages) in results) {
                val relPath = sym.file.relativeTo(rootDir).path
                sb.appendLine("## `${sym.qualifiedName}` (${sym.kind.label})")
                sb.appendLine()
                sb.appendLine("📍 **Declaration:** `$relPath:${sym.line}`")
                sb.appendLine()

                if (usages.isEmpty()) {
                    sb.appendLine("No usages found — this symbol may be unused.")
                } else {
                    // Group usages by file
                    val byFile = usages.groupBy { it.file }
                    sb.appendLine("**${usages.size} usages** across **${byFile.size} files:**")
                    sb.appendLine()

                    for ((file, refs) in byFile.entries.sortedBy { it.key.name }) {
                        val fileRelPath = file.relativeTo(rootDir).path
                        sb.appendLine("### `$fileRelPath`")
                        sb.appendLine()
                        for (ref in refs.sortedBy { it.line }) {
                            sb.appendLine("- Line ${ref.line} (${ref.kind.label}): `${ref.context}`")
                        }
                        sb.appendLine()
                    }
                }
            }
        }

        out.writeText(sb.toString())
        // Also print to console for immediate feedback
        logger.lifecycle(sb.toString().trimEnd())
    }
}
