package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import java.io.File

@UntrackedTask(because = "Extract analyzes source files dynamically")
abstract class OpenSpecExtractTask : DefaultTask() {

    @get:Input
    abstract val sourceFile: Property<String>

    @get:Input
    abstract val startLine: Property<Int>

    @get:Input
    abstract val endLine: Property<Int>

    @get:Input
    abstract val newName: Property<String>

    @get:Input @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Extract a block of code into a new function or class. " +
            "Output: .opsx/extract.md. " +
            "Options: -PsourceFile=path -PstartLine=N -PendLine=M -PnewName=Name (required), -PdryRun=true (preview only). " +
            "Use when: You want to extract a method, function, or class from existing code. " +
            "Chain: Run opsx-usages after to verify the extraction."
    }

    @TaskAction
    fun extract() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir
        val filePath = sourceFile.get()
        val start = startLine.get()
        val end = endLine.get()
        val name = newName.get()
        val preview = dryRun.getOrElse(false)

        val file = rootDir.resolve(filePath)
        if (!file.exists()) {
            val msg = "# Extract\n\n> File not found: `$filePath`\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val lines = file.readLines()
        if (start < 1 || end > lines.size || start > end) {
            val msg = "# Extract\n\n> Invalid line range: $start-$end (file has ${lines.size} lines)\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val extractedLines = lines.subList(start - 1, end)
        val extractedCode = extractedLines.joinToString("\n")

        // Analyze the extracted block for free variables (referenced but not declared)
        val declaredNames = mutableSetOf<String>()
        val referencedNames = mutableSetOf<String>()
        val valVarPattern = Regex("""(?:val|var|fun)\s+(\w+)""")
        val identifierPattern = Regex("""\b([a-z]\w+)\b""")

        for (line in extractedLines) {
            valVarPattern.findAll(line).forEach { declaredNames.add(it.groupValues[1]) }
            identifierPattern.findAll(line).forEach { referencedNames.add(it.groupValues[1]) }
        }

        val freeVars = (referencedNames - declaredNames)
            .filter { it.length > 1 } // skip single-char and common keywords
            .filterNot { it in setOf("if", "else", "when", "for", "while", "return", "val", "var", "fun",
                "class", "object", "true", "false", "null", "this", "super", "it", "in", "is",
                "as", "by", "to", "and", "or", "not", "import", "package", "private", "public",
                "internal", "protected", "override", "abstract", "open", "data", "sealed",
                "companion", "const", "lateinit", "lazy", "new", "void", "int", "long", "boolean",
                "string", "list", "map", "set") }

        // Detect the indentation level
        val indent = extractedLines.filter { it.isNotBlank() }.minOfOrNull {
            it.length - it.trimStart().length
        } ?: 0
        val baseIndent = " ".repeat(indent)

        val sb = StringBuilder()
        sb.appendLine("# Extract: `$name`")
        sb.appendLine()
        sb.appendLine("**Source:** `$filePath` lines $start–$end")
        sb.appendLine()
        sb.appendLine("## Extracted Code")
        sb.appendLine()
        sb.appendLine("```kotlin")
        sb.appendLine(extractedCode)
        sb.appendLine("```")
        sb.appendLine()

        // Suggest function signature
        if (freeVars.isNotEmpty()) {
            sb.appendLine("## Suggested Parameters")
            sb.appendLine()
            sb.appendLine("Free variables detected (may need to become parameters):")
            for (v in freeVars.sorted()) {
                sb.appendLine("- `$v`")
            }
            sb.appendLine()
        }

        sb.appendLine("## Suggested Extraction")
        sb.appendLine()
        if (freeVars.isNotEmpty()) {
            sb.appendLine("```kotlin")
            sb.appendLine("private fun $name(${freeVars.sorted().joinToString(", ") { "$it: Any /* TODO: refine type */" }}) {")
            for (line in extractedLines) {
                sb.appendLine(line.removePrefix(baseIndent).prependIndent("    "))
            }
            sb.appendLine("}")
            sb.appendLine("```")
        } else {
            sb.appendLine("```kotlin")
            sb.appendLine("private fun $name() {")
            for (line in extractedLines) {
                sb.appendLine(line.removePrefix(baseIndent).prependIndent("    "))
            }
            sb.appendLine("}")
            sb.appendLine("```")
        }
        sb.appendLine()

        // Call site replacement
        sb.appendLine("## Call Site Replacement")
        sb.appendLine()
        sb.appendLine("Replace lines $start–$end with:")
        sb.appendLine()
        if (freeVars.isNotEmpty()) {
            sb.appendLine("```kotlin")
            sb.appendLine("${baseIndent}$name(${freeVars.sorted().joinToString(", ")})")
            sb.appendLine("```")
        } else {
            sb.appendLine("```kotlin")
            sb.appendLine("${baseIndent}$name()")
            sb.appendLine("```")
        }
        sb.appendLine()

        if (preview) {
            sb.appendLine("⚠️ **DRY RUN** — no files were modified.")
            sb.appendLine("Review the extraction above and apply manually, or re-run with `-PdryRun=false`.")
        } else {
            sb.appendLine("⚠️ **Extract is suggestion-only.** The agent should review the suggested extraction,")
            sb.appendLine("adjust parameter types, and apply the refactoring. Use `opsx-usages` after to verify.")
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }
}
