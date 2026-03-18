package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.psi.SymbolKind
import java.io.File

@CacheableTask
abstract class OpenSpecSymbolsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:Input @get:Optional
    abstract val symbol: Property<String>

    @get:Input @get:Optional
    abstract val targetFile: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Symbol index — declarations and usages from PSI analysis. " +
            "Output: .opsx/symbols.md. " +
            "Options: -Psymbol=Name (filter to symbol), -Pfile=path (filter to file), -Pmodule=name. " +
            "Use when: You need to find where something is declared or used. Replaces grep. " +
            "Chain: Use before opsx-find for targeted search, or opsx-rename for safe refactoring."
    }

    @TaskAction
    fun analyze() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Symbol Index\n\n> No source files found.\n")
            logger.lifecycle("OpenSpec: No sources found for symbol analysis.")
            return
        }

        logger.lifecycle("OpenSpec: Analyzing ${sourceFiles.size} source files...")
        val index = SymbolIndex.build(sourceFiles)
        val sb = StringBuilder()

        if (symbol.isPresent) {
            // Show specific symbol usages
            renderSymbolUsages(sb, index, symbol.get(), rootDir)
        } else if (targetFile.isPresent) {
            // Show symbols in a specific file
            renderFileSymbols(sb, index, targetFile.get(), rootDir)
        } else {
            // Show full index
            renderFullIndex(sb, index, rootDir)
        }

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated symbol index at ${out.relativeTo(rootDir)}")
    }

    private fun renderFullIndex(sb: StringBuilder, index: SymbolIndex, rootDir: File) {
        sb.appendLine("# Symbol Index")
        sb.appendLine()

        val typeSymbols = index.symbols.filter {
            it.kind in setOf(SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.DATA_CLASS, SymbolKind.ENUM, SymbolKind.OBJECT)
        }
        val funSymbols = index.symbols.filter { it.kind == SymbolKind.FUNCTION }
        val propSymbols = index.symbols.filter { it.kind == SymbolKind.PROPERTY }

        sb.appendLine("**${index.symbols.size} symbols** (${typeSymbols.size} types, ${funSymbols.size} functions, ${propSymbols.size} properties) from **${index.symbols.map { it.file }.distinct().size} files**")
        sb.appendLine()

        // Types with usage counts
        sb.appendLine("## Types")
        sb.appendLine()
        sb.appendLine("| Type | Kind | Usages | Location |")
        sb.appendLine("|------|------|--------|----------|")
        for (sym in typeSymbols.sortedBy { it.qualifiedName }) {
            val usageCount = index.findUsages(sym.qualifiedName).size
            val relPath = sym.file.relativeTo(rootDir).path
            sb.appendLine("| `${sym.name}` | ${sym.kind.label} | $usageCount | `$relPath:${sym.line}` |")
        }
        sb.appendLine()

        // Most-used symbols
        val usageCounts = index.usageCounts().take(10)
        if (usageCounts.isNotEmpty()) {
            sb.appendLine("## Most Used")
            sb.appendLine()
            for ((sym, count) in usageCounts) {
                sb.appendLine("- `${sym.name}` — $count usages")
            }
            sb.appendLine()
        }
    }

    private fun renderSymbolUsages(sb: StringBuilder, index: SymbolIndex, name: String, rootDir: File) {
        val results = index.findUsagesByName(name)
        sb.appendLine("# Symbol: `$name`")
        sb.appendLine()

        if (results.isEmpty()) {
            sb.appendLine("> No symbol found matching `$name`.")
            return
        }

        for ((sym, usages) in results) {
            val relPath = sym.file.relativeTo(rootDir).path
            sb.appendLine("## `${sym.qualifiedName}` (${sym.kind.label})")
            sb.appendLine()
            sb.appendLine("**Declared at:** `$relPath:${sym.line}`")
            sb.appendLine()

            if (usages.isEmpty()) {
                sb.appendLine("> No usages found.")
            } else {
                sb.appendLine("**${usages.size} usages:**")
                sb.appendLine()
                for (ref in usages.sortedBy { "${it.file.name}:${it.line}" }) {
                    val refPath = ref.file.relativeTo(rootDir).path
                    sb.appendLine("- `$refPath:${ref.line}` (${ref.kind.label}) — `${ref.context}`")
                }
            }
            sb.appendLine()
        }
    }

    private fun renderFileSymbols(sb: StringBuilder, index: SymbolIndex, path: String, rootDir: File) {
        val targetFile = File(rootDir, path)
        val syms = index.symbolsInFile(targetFile)

        sb.appendLine("# Symbols in `$path`")
        sb.appendLine()

        if (syms.isEmpty()) {
            sb.appendLine("> No symbols found in `$path`.")
            return
        }

        for (sym in syms.sortedBy { it.line }) {
            val usageCount = index.findUsages(sym.qualifiedName).size
            sb.appendLine("- `${sym.name}` (${sym.kind.label}) line ${sym.line} — $usageCount usages")
        }
        sb.appendLine()
    }
}
