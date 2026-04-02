package zone.clanker.gradle.tasks.refactoring

import zone.clanker.gradle.tasks.SRCX_GROUP

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import java.io.File

@UntrackedTask(because = "Move modifies source files and directories across the project")
abstract class MoveTask : DefaultTask() {

    @get:Input
    abstract val symbol: Property<String>

    @get:Input
    abstract val targetPackage: Property<String>

    @get:Input @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = SRCX_GROUP
        description = "[tool] Move a class/file to a different package, updating all imports. " +
            "Output: .opsx/move.md. " +
            "Options: -Psymbol=ClassName -PtargetPackage=new.pkg (required), -PdryRun=true (preview only). " +
            "Use when: You need to relocate a class to another package safely. " +
            "Chain: Run srcx-find first to check impact."
    }

    @get:Input @get:Optional
    abstract val force: Property<Boolean>

    @TaskAction
    fun move() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir
        val name = symbol.get()
        val newPkg = targetPackage.get()
        val preview = dryRun.getOrElse(false)

        // Validate targetPackage matches a valid Java/Kotlin package pattern
        val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$")
        if (!packagePattern.matches(newPkg)) {
            val msg = "# Move\n\n> Invalid target package `$newPkg`. " +
                "Package names must contain only alphanumeric characters, underscores, and dots.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Move\n\n> No source files found.\n")
            return
        }

        logger.lifecycle("OpenSpec: Computing move `$name` → `$newPkg`...")
        val index = SymbolIndex.build(sourceFiles)

        // Find the symbol to move
        val symbols = index.symbols.filter { it.name == name }
        if (symbols.isEmpty()) {
            val msg = "# Move: `$name`\n\n> No symbol found matching `$name`.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val sym = if (symbols.size == 1) symbols.first() else {
            // Prefer class-level symbols
            symbols.firstOrNull { it.kind in setOf(
                zone.clanker.gradle.core.SymbolKind.CLASS,
                zone.clanker.gradle.core.SymbolKind.DATA_CLASS,
                zone.clanker.gradle.core.SymbolKind.INTERFACE,
                zone.clanker.gradle.core.SymbolKind.ENUM,
                zone.clanker.gradle.core.SymbolKind.OBJECT,
            ) } ?: symbols.first()
        }

        val oldPkg = sym.packageName
        val oldQualified = sym.qualifiedName
        val newQualified = "$newPkg.${sym.name}"

        if (oldPkg == newPkg) {
            val msg = "# Move: `$name`\n\n> Symbol is already in package `$newPkg`.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        val sb = StringBuilder()
        sb.appendLine("# Move: `$oldQualified` → `$newQualified`")
        sb.appendLine()

        // 1. Compute target file path
        val sourceFile = sym.file
        val srcDir = srcDirs.firstOrNull { sourceFile.startsWith(it) } ?: sourceFile.parentFile
        val newRelPath = newPkg.replace('.', File.separatorChar) + File.separator + sourceFile.name
        val newFile = File(srcDir, newRelPath)

        // Verify the target file resolves under the source root (prevent path traversal)
        if (!newFile.canonicalPath.startsWith(srcDir.canonicalPath + File.separator)) {
            val msg = "# Move\n\n> Target path escapes source root. Refusing to proceed.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        // Check target file doesn't already exist (unless force flag is set)
        if (newFile.exists() && !force.getOrElse(false)) {
            val msg = "# Move\n\n> Target file already exists: `${newFile.relativeTo(rootDir).path}`. " +
                "Use `-Pforce=true` to overwrite.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        sb.appendLine("## File Move")
        sb.appendLine()
        sb.appendLine("- **From:** `${sourceFile.relativeTo(rootDir).path}`")
        sb.appendLine("- **To:** `${newFile.relativeTo(rootDir).path}`")
        sb.appendLine()

        // 2. Find all files that import the old symbol
        val importEdits = mutableListOf<ImportEdit>()
        for (file in sourceFiles) {
            val lines = file.readLines()
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trim()
                when {
                    trimmed == "import $oldQualified" ->
                        importEdits.add(ImportEdit(file, idx + 1, line, "import $newQualified"))
                    trimmed == "import ${oldPkg}.*" ->
                        importEdits.add(ImportEdit(file, idx + 1, line, "import $newQualified"))
                    trimmed.startsWith("import $oldQualified as ") -> {
                        val alias = trimmed.substringAfter("import $oldQualified as ").trim()
                        importEdits.add(ImportEdit(file, idx + 1, line, "import $newQualified as $alias"))
                    }
                }
            }
        }

        // 3. Report import updates
        if (importEdits.isNotEmpty()) {
            val byFile = importEdits.groupBy { it.file }
            sb.appendLine("## Import Updates")
            sb.appendLine()
            sb.appendLine("**${importEdits.size} imports** across **${byFile.size} files**")
            sb.appendLine()
            for ((file, edits) in byFile.entries.sortedBy { it.key.name }) {
                val relPath = file.relativeTo(rootDir).path
                sb.appendLine("### `$relPath`")
                for (edit in edits.sortedBy { it.line }) {
                    sb.appendLine("- Line ${edit.line}: `${edit.oldLine.trim()}` → `${edit.newImport}`")
                }
                sb.appendLine()
            }
        } else {
            sb.appendLine("No import statements reference this symbol.")
            sb.appendLine()
        }

        if (preview) {
            sb.appendLine("⚠️ **DRY RUN** — no files were modified.")
        } else {
            // Apply: update package declaration in source file
            val sourceContent = sourceFile.readText()
            val updatedContent = sourceContent.replaceFirst(
                "package $oldPkg",
                "package $newPkg"
            )
            newFile.parentFile.mkdirs()
            newFile.writeText(updatedContent)
            if (!newFile.exists() || newFile.readText() != updatedContent) {
                throw org.gradle.api.GradleException("Failed to write moved file to ${newFile.path}")
            }
            sourceFile.delete()

            // Clean up empty parent dirs
            var parent = sourceFile.parentFile
            while (parent != null && parent != rootDir && parent.listFiles()?.isEmpty() == true) {
                parent.delete()
                parent = parent.parentFile
            }

            // Apply: update imports in all referencing files
            for (edit in importEdits) {
                val content = edit.file.readText()
                val updated = content.replace(edit.oldLine.trim(), edit.newImport)
                edit.file.writeText(updated)
            }

            sb.appendLine("✅ **Applied.** File moved and ${importEdits.size} imports updated.")
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }

    private data class ImportEdit(
        val file: File,
        val line: Int,
        val oldLine: String,
        val newImport: String,
    )
}
