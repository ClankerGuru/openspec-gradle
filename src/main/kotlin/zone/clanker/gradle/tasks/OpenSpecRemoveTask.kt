package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.psi.SymbolKind
import java.io.File

@UntrackedTask(because = "Remove modifies source files dynamically")
abstract class OpenSpecRemoveTask : DefaultTask() {

    @get:Input @get:Optional
    abstract val symbol: Property<String>

    @get:Input @get:Optional
    abstract val sourceFile: Property<String>

    @get:Input @get:Optional
    abstract val startLine: Property<Int>

    @get:Input @get:Optional
    abstract val endLine: Property<Int>

    @get:Input @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Remove a symbol or line range from the codebase, cleaning up imports. " +
            "Output: .opsx/remove.md. " +
            "Options: -Psymbol=Name or -Pfile=path -PstartLine=N -PendLine=M, -PdryRun=true (preview only). " +
            "Use when: You need to delete a class, method, or code block and clean up references. " +
            "Chain: Run opsx-usages first for impact analysis."
    }

    @TaskAction
    fun remove() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir
        val preview = dryRun.getOrElse(true)

        val hasSymbol = symbol.isPresent
        val hasFile = sourceFile.isPresent

        if (!hasSymbol && !hasFile) {
            val msg = "# Remove\n\n> Must specify either `-Psymbol=Name` or `-Pfile=path -PstartLine=N -PendLine=M`.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        if (hasFile) {
            removeLineRange(out, rootDir, preview)
        } else {
            removeSymbol(out, rootDir, preview)
        }
    }

    private fun removeLineRange(out: File, rootDir: File, preview: Boolean) {
        val filePath = sourceFile.get()
        val file = rootDir.resolve(filePath)
        if (!file.exists()) {
            val msg = "# Remove\n\n> File not found: `$filePath`\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        if (!startLine.isPresent || !endLine.isPresent) {
            val msg = "# Remove\n\n> Line range mode requires both `-PstartLine=N` and `-PendLine=M`.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val start = startLine.get()
        val end = endLine.get()
        val lines = file.readLines()

        if (start < 1 || end > lines.size || start > end) {
            val msg = "# Remove\n\n> Invalid line range: $start–$end (file has ${lines.size} lines)\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val removedLines = lines.subList(start - 1, end)

        val sb = StringBuilder()
        sb.appendLine("# Remove: lines $start–$end from `$filePath`")
        sb.appendLine()
        sb.appendLine("## Code to Remove")
        sb.appendLine()
        sb.appendLine("```")
        for ((i, line) in removedLines.withIndex()) {
            sb.appendLine("${start + i}: $line")
        }
        sb.appendLine("```")
        sb.appendLine()

        if (preview) {
            sb.appendLine("⚠️ **DRY RUN** — no files were modified.")
            sb.appendLine("Re-run with `-PdryRun=false` to apply.")
        } else {
            val remaining = lines.toMutableList()
            remaining.subList(start - 1, end).clear()
            file.writeText(remaining.joinToString("\n", postfix = if (lines.last().isEmpty()) "\n" else ""))
            sb.appendLine("✅ **Applied.** Removed ${removedLines.size} lines from `$filePath`.")
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }

    private fun removeSymbol(out: File, rootDir: File, preview: Boolean) {
        val name = symbol.get()
        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Remove\n\n> No source files found.\n")
            return
        }

        logger.lifecycle("OpenSpec: Computing removal of `$name` in ${sourceFiles.size} files...")
        val index = SymbolIndex.build(sourceFiles)

        // Determine if this is a member removal (ClassName.memberName)
        val isMemberRemoval = name.contains(".")
        val className = if (isMemberRemoval) name.substringBeforeLast(".") else name
        val memberName = if (isMemberRemoval) name.substringAfterLast(".") else null

        // Find the target symbol
        val classSymbols = index.symbols.filter { it.name == className }
        if (classSymbols.isEmpty()) {
            val msg = "# Remove: `$name`\n\n> No symbol found matching `$className`.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val classSym = if (classSymbols.size == 1) classSymbols.first() else {
            classSymbols.firstOrNull { it.kind in setOf(
                SymbolKind.CLASS, SymbolKind.DATA_CLASS, SymbolKind.INTERFACE,
                SymbolKind.ENUM, SymbolKind.OBJECT,
            ) } ?: classSymbols.first()
        }

        val sb = StringBuilder()

        if (isMemberRemoval) {
            removeMember(sb, classSym, memberName!!, index, sourceFiles, rootDir, preview)
        } else {
            removeClass(sb, classSym, index, sourceFiles, rootDir, preview)
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }

    private fun removeClass(
        sb: StringBuilder,
        sym: zone.clanker.gradle.psi.Symbol,
        index: SymbolIndex,
        sourceFiles: List<File>,
        rootDir: File,
        preview: Boolean,
    ) {
        val relPath = sym.file.relativeTo(rootDir).path
        sb.appendLine("# Remove: `${sym.qualifiedName}` (${sym.kind.label})")
        sb.appendLine()
        sb.appendLine("📍 **Declaration:** `$relPath:${sym.line}`")
        sb.appendLine()

        // Find usages
        val wordBoundary = Regex("\\b${Regex.escape(sym.name)}\\b")
        val importPattern = Regex("^\\s*import\\s+.*\\b${Regex.escape(sym.name)}\\b")

        data class Usage(val file: File, val line: Int, val kind: String, val context: String)

        val usages = mutableListOf<Usage>()
        val importLines = mutableListOf<Usage>() // imports to clean up

        for (file in sourceFiles) {
            if (file == sym.file) continue
            val lines = file.readLines()
            lines.forEachIndexed { idx, line ->
                val lineNum = idx + 1
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("package ")) return@forEachIndexed
                if (wordBoundary.containsMatchIn(line)) {
                    if (importPattern.matches(line)) {
                        importLines.add(Usage(file, lineNum, "import", trimmed))
                    } else {
                        val kind = when {
                            trimmed.contains("${sym.name}(") -> "call"
                            trimmed.contains(": ${sym.name}") || trimmed.contains("<${sym.name}>") -> "type-ref"
                            trimmed.contains("extends ${sym.name}") || trimmed.contains("implements ${sym.name}") -> "supertype"
                            else -> "reference"
                        }
                        usages.add(Usage(file, lineNum, kind, trimmed))
                    }
                }
            }
        }

        // Report declaration
        val fileLines = sym.file.readLines()
        val declStart = sym.line - 1
        // Find the end of the declaration (matching braces or single statement)
        val declEnd = findDeclarationEnd(fileLines, declStart)
        val declLineCount = declEnd - declStart + 1

        sb.appendLine("## Declaration to Remove")
        sb.appendLine()
        sb.appendLine("```")
        for (i in declStart..minOf(declEnd, fileLines.size - 1)) {
            sb.appendLine("${i + 1}: ${fileLines[i]}")
        }
        sb.appendLine("```")
        sb.appendLine()

        // Report imports to clean
        if (importLines.isNotEmpty()) {
            val byFile = importLines.groupBy { it.file }
            sb.appendLine("## Imports to Clean (${importLines.size} across ${byFile.size} files)")
            sb.appendLine()
            for ((file, locs) in byFile.entries.sortedBy { it.key.name }) {
                val fileRel = file.relativeTo(rootDir).path
                for (loc in locs) {
                    sb.appendLine("- `$fileRel:${loc.line}`: `${loc.context}`")
                }
            }
            sb.appendLine()
        }

        // Report remaining references that will break
        if (usages.isNotEmpty()) {
            val byFile = usages.groupBy { it.file }
            sb.appendLine("## ⚠️ Remaining References (${usages.size} across ${byFile.size} files)")
            sb.appendLine()
            sb.appendLine("These references will break after removal:")
            sb.appendLine()
            for ((file, locs) in byFile.entries.sortedBy { it.key.name }) {
                val fileRel = file.relativeTo(rootDir).path
                for (loc in locs) {
                    sb.appendLine("- `$fileRel:${loc.line}` (${loc.kind}): `${loc.context.take(100)}`")
                }
            }
            sb.appendLine()
        }

        if (preview) {
            sb.appendLine("⚠️ **DRY RUN** — no files were modified.")
            sb.appendLine("Re-run with `-PdryRun=false` to apply.")
        } else {
            // Remove the declaration
            val remaining = fileLines.toMutableList()
            remaining.subList(declStart, minOf(declEnd + 1, remaining.size)).clear()
            // Clean up consecutive blank lines left behind
            sym.file.writeText(remaining.joinToString("\n") + "\n")

            // Clean up imports — process bottom-to-top per file to preserve line indices
            for ((file, imps) in importLines.groupBy { it.file }) {
                val content = file.readLines().toMutableList()
                for (imp in imps.sortedByDescending { it.line }) {
                    val idx = imp.line - 1
                    if (idx in content.indices) {
                        content.removeAt(idx)
                    }
                }
                file.writeText(content.joinToString("\n") + "\n")
            }

            sb.appendLine("✅ **Applied.** Removed `${sym.qualifiedName}` ($declLineCount lines) and cleaned ${importLines.size} imports.")
            if (usages.isNotEmpty()) {
                sb.appendLine("⚠️ ${usages.size} remaining references will need manual cleanup.")
            }
        }
    }

    private fun removeMember(
        sb: StringBuilder,
        classSym: zone.clanker.gradle.psi.Symbol,
        memberName: String,
        index: SymbolIndex,
        sourceFiles: List<File>,
        rootDir: File,
        preview: Boolean,
    ) {
        val relPath = classSym.file.relativeTo(rootDir).path
        sb.appendLine("# Remove: `${classSym.name}.$memberName`")
        sb.appendLine()

        // Find the member in the class file
        val fileLines = classSym.file.readLines()
        val memberPattern = Regex("""(fun|val|var|override\s+fun|override\s+val|override\s+var|private\s+fun|internal\s+fun|protected\s+fun)\s+${Regex.escape(memberName)}\b""")

        var memberStart = -1
        for (i in classSym.line until fileLines.size) {
            if (memberPattern.containsMatchIn(fileLines[i])) {
                memberStart = i
                break
            }
        }

        if (memberStart == -1) {
            sb.appendLine("> Member `$memberName` not found in `${classSym.name}` (`$relPath`).")
            outputFile.get().asFile.writeText(sb.toString())
            return
        }

        val memberEnd = findDeclarationEnd(fileLines, memberStart)
        val memberLineCount = memberEnd - memberStart + 1

        sb.appendLine("📍 **Declaration:** `$relPath:${memberStart + 1}`")
        sb.appendLine()
        sb.appendLine("## Code to Remove")
        sb.appendLine()
        sb.appendLine("```")
        for (i in memberStart..minOf(memberEnd, fileLines.size - 1)) {
            sb.appendLine("${i + 1}: ${fileLines[i]}")
        }
        sb.appendLine("```")
        sb.appendLine()

        // Find usages of the member
        val wordBoundary = Regex("\\b${Regex.escape(memberName)}\\b")

        data class Usage(val file: File, val line: Int, val kind: String, val context: String)
        val usages = mutableListOf<Usage>()

        for (file in sourceFiles) {
            val lines = file.readLines()
            lines.forEachIndexed { idx, line ->
                val lineNum = idx + 1
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("package ") || trimmed.startsWith("import ")) return@forEachIndexed
                if (file == classSym.file && idx in memberStart..memberEnd) return@forEachIndexed
                if (wordBoundary.containsMatchIn(line)) {
                    val kind = when {
                        trimmed.contains(".$memberName(") || trimmed.contains(".$memberName ") -> "call"
                        trimmed.contains(".$memberName") -> "reference"
                        trimmed.contains("${memberName}(") -> "call"
                        else -> "reference"
                    }
                    usages.add(Usage(file, lineNum, kind, trimmed))
                }
            }
        }

        if (usages.isNotEmpty()) {
            val byFile = usages.groupBy { it.file }
            sb.appendLine("## ⚠️ Remaining References (${usages.size} across ${byFile.size} files)")
            sb.appendLine()
            for ((file, locs) in byFile.entries.sortedBy { it.key.name }) {
                val fileRel = file.relativeTo(rootDir).path
                for (loc in locs) {
                    sb.appendLine("- `$fileRel:${loc.line}` (${loc.kind}): `${loc.context.take(100)}`")
                }
            }
            sb.appendLine()
        }

        if (preview) {
            sb.appendLine("⚠️ **DRY RUN** — no files were modified.")
            sb.appendLine("Re-run with `-PdryRun=false` to apply.")
        } else {
            val remaining = fileLines.toMutableList()
            remaining.subList(memberStart, minOf(memberEnd + 1, remaining.size)).clear()
            classSym.file.writeText(remaining.joinToString("\n") + "\n")

            sb.appendLine("✅ **Applied.** Removed `${classSym.name}.$memberName` ($memberLineCount lines).")
            if (usages.isNotEmpty()) {
                sb.appendLine("⚠️ ${usages.size} remaining references will need manual cleanup.")
            }
        }
    }

    /**
     * Find the end line of a declaration starting at [startIdx].
     * Tracks brace depth to find the matching closing brace.
     *
     * Note: This does not account for braces inside string literals or comments.
     * This is a known limitation shared with other regex-based analysis in the plugin.
     */
    private fun findDeclarationEnd(lines: List<String>, startIdx: Int): Int {
        var braceDepth = 0
        var foundOpen = false

        for (i in startIdx until lines.size) {
            val line = lines[i]
            for (ch in line) {
                when (ch) {
                    '{' -> { braceDepth++; foundOpen = true }
                    '}' -> { braceDepth-- }
                }
            }
            // If we found an opening brace and depth returned to 0, we're done
            if (foundOpen && braceDepth <= 0) return i
            // Single-line declaration without braces (e.g., `fun foo() = expr`)
            if (!foundOpen && i == startIdx && !line.trimEnd().endsWith("{")) {
                // Check if it's a single-line expression
                if (line.contains("=") && !line.trimEnd().endsWith(",")) return i
            }
        }
        // Fallback: return startIdx if nothing matched
        return if (foundOpen) lines.size - 1 else startIdx
    }
}
