package zone.clanker.gradle.tasks.discovery

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.core.SymbolKind
import java.io.File

@CacheableTask
abstract class SymbolsTask : DefaultTask() {

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
            "Chain: Use before srcx-find for targeted search, or srcx-rename for safe refactoring."
    }

    @TaskAction
    fun analyze() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val root = project.rootProject
        val rootDir = root.projectDir
        val isMultiModule = root.subprojects.isNotEmpty() && !module.isPresent && !symbol.isPresent && !targetFile.isPresent

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val allSourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (allSourceFiles.isEmpty()) {
            out.writeText("# Symbol Index\n\n> No source files found.\n")
            logger.lifecycle("OpenSpec: No sources found for symbol analysis.")
            return
        }

        logger.lifecycle("OpenSpec: Analyzing ${allSourceFiles.size} source files...")

        // For filtered queries (symbol/file), always write single file
        if (symbol.isPresent || targetFile.isPresent) {
            val index = SymbolIndex.build(allSourceFiles)
            val sb = StringBuilder()
            if (symbol.isPresent) {
                renderSymbolUsages(sb, index, symbol.get(), rootDir)
            } else {
                renderFileSymbols(sb, index, targetFile.get(), rootDir)
            }
            out.writeText(sb.toString())
            logger.lifecycle("OpenSpec: Generated symbol index at ${out.relativeTo(rootDir)}")
            return
        }

        if (isMultiModule) {
            val subDir = out.parentFile.resolve("symbols")
            subDir.mkdirs()

            data class ModuleStats(val name: String, val displayName: String, val symbols: Int, val types: Int, val functions: Int)
            val stats = mutableListOf<ModuleStats>()

            val allProjects = listOf(root) + root.subprojects.sortedBy { it.path }
            for (proj in allProjects) {
                val projSrcDirs = SourceDiscovery.discoverSourceDirs(proj)
                val projFiles = SourceDiscovery.collectSourceFiles(projSrcDirs)
                if (projFiles.isEmpty()) continue

                val moduleName = if (proj == root) "root" else proj.name
                val displayName = if (proj == root) ":root" else proj.path

                val index = SymbolIndex.build(projFiles)
                val sb = StringBuilder()
                renderFullIndex(sb, index, rootDir)
                File(subDir, "$moduleName.md").writeText(sb.toString())

                val typeCount = index.symbols.count { it.kind in setOf(SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.DATA_CLASS, SymbolKind.ENUM, SymbolKind.OBJECT) }
                val funCount = index.symbols.count { it.kind == SymbolKind.FUNCTION }
                stats.add(ModuleStats(moduleName, displayName, index.symbols.size, typeCount, funCount))
            }

            val idx = StringBuilder()
            idx.appendLine("# Symbol Index")
            idx.appendLine()
            if (stats.isEmpty()) {
                idx.appendLine("> No symbols found.")
            } else {
                idx.appendLine("| Module | Symbols | Types | Functions |")
                idx.appendLine("|--------|---------|-------|-----------|")
                for (s in stats) {
                    idx.appendLine("| [${s.displayName}](symbols/${s.name}.md) | ${s.symbols} | ${s.types} | ${s.functions} |")
                }
            }
            idx.appendLine()
            out.writeText(idx.toString())
            logger.lifecycle("OpenSpec: Generated symbol index (${stats.size} modules) at ${out.relativeTo(rootDir)}")
        } else {
            val index = SymbolIndex.build(allSourceFiles)
            val sb = StringBuilder()
            renderFullIndex(sb, index, rootDir)
            out.writeText(sb.toString())
            logger.lifecycle("OpenSpec: Generated symbol index at ${out.relativeTo(rootDir)}")
        }
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

        // Types with usage locations
        sb.appendLine("## Types")
        sb.appendLine()
        for (sym in typeSymbols.sortedBy { it.qualifiedName }) {
            val usages = index.findUsages(sym.qualifiedName)
            val relPath = sym.file.relativeTo(rootDir).path
            sb.appendLine("### `${sym.name}` (${sym.kind.label})")
            sb.appendLine()
            sb.appendLine("📍 `$relPath:${sym.line}`")
            sb.appendLine()
            if (usages.isEmpty()) {
                sb.appendLine("_No usages found._")
            } else {
                sb.appendLine("**${usages.size} usages:**")
                sb.appendLine()
                for (ref in usages.sortedBy { "${it.file.name}:${it.line}" }) {
                    val refPath = ref.file.relativeTo(rootDir).path
                    sb.appendLine("- `$refPath:${ref.line}` (${ref.kind.label}) — `${ref.context.take(100)}`")
                }
            }
            sb.appendLine()
        }

        // Functions with usage locations
        if (funSymbols.isNotEmpty()) {
            sb.appendLine("## Functions")
            sb.appendLine()
            for (sym in funSymbols.sortedBy { it.qualifiedName }) {
                val usages = index.findUsages(sym.qualifiedName)
                val relPath = sym.file.relativeTo(rootDir).path
                if (usages.isEmpty()) {
                    sb.appendLine("- `${sym.name}` — `$relPath:${sym.line}` — _no usages_")
                } else {
                    sb.appendLine("- `${sym.name}` — `$relPath:${sym.line}` — **${usages.size} usages:**")
                    for (ref in usages.sortedBy { "${it.file.name}:${it.line}" }) {
                        val refPath = ref.file.relativeTo(rootDir).path
                        sb.appendLine("  - `$refPath:${ref.line}` (${ref.kind.label}) — `${ref.context.take(100)}`")
                    }
                }
            }
            sb.appendLine()
        }

        // Properties
        if (propSymbols.isNotEmpty()) {
            sb.appendLine("## Properties")
            sb.appendLine()
            for (sym in propSymbols.sortedBy { it.qualifiedName }) {
                val usages = index.findUsages(sym.qualifiedName)
                val relPath = sym.file.relativeTo(rootDir).path
                if (usages.isEmpty()) {
                    sb.appendLine("- `${sym.name}` — `$relPath:${sym.line}` — _no usages_")
                } else {
                    sb.appendLine("- `${sym.name}` — `$relPath:${sym.line}` — **${usages.size} usages:**")
                    for (ref in usages.sortedBy { "${it.file.name}:${it.line}" }) {
                        val refPath = ref.file.relativeTo(rootDir).path
                        sb.appendLine("  - `$refPath:${ref.line}` (${ref.kind.label}) — `${ref.context.take(100)}`")
                    }
                }
            }
            sb.appendLine()
        }

        // Most-used symbols
        val usageCounts = index.usageCounts().take(10)
        if (usageCounts.isNotEmpty()) {
            sb.appendLine("## Most Used")
            sb.appendLine()
            for ((sym, count) in usageCounts) {
                val usages = index.findUsages(sym.qualifiedName)
                sb.appendLine("### `${sym.name}` — $count usages")
                sb.appendLine()
                for (ref in usages.sortedBy { "${it.file.name}:${it.line}" }) {
                    val refPath = ref.file.relativeTo(rootDir).path
                    sb.appendLine("- `$refPath:${ref.line}` (${ref.kind.label}) — `${ref.context.take(100)}`")
                }
                sb.appendLine()
            }
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
