package zone.clanker.gradle.psi

import java.io.File

/**
 * Safe rename across the codebase using the symbol index.
 */
class Renamer(private val index: SymbolIndex) {

    data class RenameEdit(
        val file: File,
        val line: Int,
        val oldText: String,
        val newText: String,
        val kind: String,
    )

    /**
     * Compute all edits needed to rename a symbol.
     * Does NOT apply them — returns a list for preview or execution.
     */
    fun computeRename(fromName: String, toName: String): List<RenameEdit> {
        val edits = mutableListOf<RenameEdit>()
        val matches = index.findUsagesByName(fromName)

        if (matches.isEmpty()) return edits

        for ((symbol, usages) in matches) {
            val simpleName = symbol.name.substringAfterLast('.')

            // Rename the declaration itself
            edits.add(RenameEdit(
                file = symbol.file,
                line = symbol.line,
                oldText = simpleName,
                newText = toName,
                kind = "declaration",
            ))

            // Rename all usages
            for (ref in usages) {
                edits.add(RenameEdit(
                    file = ref.file,
                    line = ref.line,
                    oldText = ref.targetName,
                    newText = if (ref.kind == ReferenceKind.IMPORT) {
                        // For imports, replace the last segment of the qualified name
                        toName
                    } else {
                        toName
                    },
                    kind = ref.kind.label,
                ))
            }
        }

        return edits.distinctBy { "${it.file.absolutePath}:${it.line}:${it.kind}" }
    }

    /**
     * Apply rename edits to the filesystem.
     * Returns the list of modified files.
     */
    fun applyRename(edits: List<RenameEdit>): List<File> {
        val byFile = edits.groupBy { it.file }
        val modified = mutableListOf<File>()

        for ((file, fileEdits) in byFile) {
            val lines = file.readLines().toMutableList()
            // Process from bottom to top to preserve line numbers
            for (edit in fileEdits.sortedByDescending { it.line }) {
                val lineIdx = edit.line - 1
                if (lineIdx in lines.indices) {
                    lines[lineIdx] = lines[lineIdx].replace(edit.oldText, edit.newText)
                }
            }
            file.writeText(lines.joinToString("\n") + if (file.readText().endsWith("\n")) "\n" else "")
            modified.add(file)
        }

        // Rename files if the declaration was a class/object and the file matches
        for (edit in edits.filter { it.kind == "declaration" }) {
            val file = edit.file
            val expectedOldName = "${edit.oldText}.${file.extension}"
            if (file.name == expectedOldName) {
                val newFile = File(file.parentFile, "${edit.newText}.${file.extension}")
                if (!newFile.exists()) {
                    file.renameTo(newFile)
                }
            }
        }

        return modified.distinct()
    }
}
