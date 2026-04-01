package zone.clanker.gradle.tasks.intelligence

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import java.io.File

@UntrackedTask(because = "Usages reads all project sources dynamically")
abstract class UsagesTask : DefaultTask() {

    @get:Input
    abstract val symbol: Property<String>

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Show all usages of a symbol with file:line locations. " +
            "Output: .opsx/usages.md. " +
            "Options: -Psymbol=Name (required). " +
            "Use when: You need to see WHERE a symbol is used (file:line), not just IF it's used. " +
            "Chain: Use before srcx-rename or srcx-move for impact analysis."
    }

    @TaskAction
    fun usages() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir
        val name = symbol.get()

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Usages: `$name`\n\n> No source files found.\n")
            return
        }

        logger.lifecycle("OpenSpec: Finding usages of `$name` in ${sourceFiles.size} files...")
        val index = SymbolIndex.build(sourceFiles)

        // Find all symbols matching the name
        val matchingSymbols = index.symbols.filter { it.name == name }

        val sb = StringBuilder()
        sb.appendLine("# Usages: `$name`")
        sb.appendLine()

        if (matchingSymbols.isEmpty()) {
            sb.appendLine("> No symbol found matching `$name`.")
            out.writeText(sb.toString())
            logger.lifecycle(sb.toString().trimEnd())
            return
        }

        for (sym in matchingSymbols) {
            val relPath = sym.file.relativeTo(rootDir).path
            sb.appendLine("## `${sym.qualifiedName}` (${sym.kind.label})")
            sb.appendLine()
            sb.appendLine("📍 **Declared at:** `$relPath:${sym.line}`")
            sb.appendLine()

            val wordBoundary = Regex("\\b${Regex.escape(sym.name)}\\b")

            // Find all lines in all files that reference this symbol
            val usageLocations = mutableListOf<UsageLocation>()
            for (file in sourceFiles) {
                if (file == sym.file) continue // skip declaration file for cleaner output
                val lines = file.readLines()
                lines.forEachIndexed { idx, line ->
                    val lineNum = idx + 1
                    val trimmed = line.trim()
                    // Skip blank lines and package declarations
                    if (trimmed.isBlank() || trimmed.startsWith("package ")) return@forEachIndexed
                    // Check if line references the symbol name (word boundary)
                    if (wordBoundary.containsMatchIn(line)) {
                        val kind = when {
                            trimmed.startsWith("import ") -> "import"
                            trimmed.contains("${sym.name}(") -> "call"
                            trimmed.contains(": ${sym.name}") || trimmed.contains("<${sym.name}>") -> "type-ref"
                            trimmed.contains("extends ${sym.name}") || trimmed.contains("implements ${sym.name}") -> "supertype"
                            else -> "reference"
                        }
                        usageLocations.add(UsageLocation(file, lineNum, kind, trimmed))
                    }
                }
            }

            // Also check same-file usages (excluding the declaration line itself)
            val selfLines = sym.file.readLines()
            selfLines.forEachIndexed { idx, line ->
                val lineNum = idx + 1
                if (lineNum == sym.line) return@forEachIndexed
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("package ")) return@forEachIndexed
                if (wordBoundary.containsMatchIn(line)) {
                    val kind = when {
                        trimmed.startsWith("import ") -> "import"
                        trimmed.contains("${sym.name}(") -> "call"
                        else -> "self-reference"
                    }
                    usageLocations.add(UsageLocation(sym.file, lineNum, kind, trimmed))
                }
            }

            if (usageLocations.isEmpty()) {
                sb.appendLine("⚠️ No usages found — this symbol may be unused.")
                sb.appendLine()
            } else {
                val byFile = usageLocations.groupBy { it.file }
                sb.appendLine("**${usageLocations.size} usages** across **${byFile.size} files:**")
                sb.appendLine()

                for ((file, locs) in byFile.entries.sortedBy { it.key.name }) {
                    val fileRel = file.relativeTo(rootDir).path
                    sb.appendLine("### `$fileRel`")
                    sb.appendLine()
                    for (loc in locs.sortedBy { it.line }) {
                        sb.appendLine("- `${fileRel}:${loc.line}` (${loc.kind}): `${loc.context.take(100)}`")
                    }
                    sb.appendLine()
                }
            }
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }

    private data class UsageLocation(
        val file: File,
        val line: Int,
        val kind: String,
        val context: String,
    )
}
