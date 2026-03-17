package zone.clanker.gradle.psi

import java.io.File

/**
 * Cross-referenced symbol index built from PSI analysis.
 * Maps every declaration to its usages and vice versa.
 */
class SymbolIndex(
    val symbols: List<Symbol>,
    val references: List<Reference>,
) {
    /** All symbols by qualified name */
    private val byQualifiedName: Map<String, Symbol> = symbols.associateBy { it.qualifiedName }

    /** All symbols by simple name (may have duplicates) */
    private val bySimpleName: Map<String, List<Symbol>> = symbols.groupBy { it.name.substringAfterLast('.') }

    /**
     * Resolve a reference to its target symbol.
     * Uses qualified name from imports, then falls back to simple name match.
     */
    fun resolve(ref: Reference): Symbol? {
        // Exact qualified name match
        if (ref.targetQualifiedName != null) {
            byQualifiedName[ref.targetQualifiedName]?.let { return it }
        }
        // Simple name match — unique wins
        val candidates = bySimpleName[ref.targetName] ?: return null
        if (candidates.size == 1) return candidates.first()
        // If multiple, try to resolve via imports in the same file
        val fileImports = references.filter { it.file == ref.file && it.kind == ReferenceKind.IMPORT }
        for (imp in fileImports) {
            if (imp.targetName == ref.targetName && imp.targetQualifiedName != null) {
                byQualifiedName[imp.targetQualifiedName]?.let { return it }
            }
        }
        return null
    }

    /** Find all usages of a symbol (by qualified name) */
    fun findUsages(qualifiedName: String): List<Reference> {
        val symbol = byQualifiedName[qualifiedName] ?: return emptyList()
        val simpleName = symbol.name.substringAfterLast('.')
        return references.filter { ref ->
            // Exclude self-declarations
            if (ref.file == symbol.file && ref.line == symbol.line) return@filter false
            when {
                ref.targetQualifiedName == qualifiedName -> true
                ref.targetName == simpleName -> resolve(ref)?.qualifiedName == qualifiedName
                else -> false
            }
        }
    }

    /** Find all usages of a symbol by simple name (matches any with that name) */
    fun findUsagesByName(name: String): List<Pair<Symbol, List<Reference>>> {
        val matchingSymbols = symbols.filter {
            it.name.substringAfterLast('.') == name || it.name == name || it.qualifiedName == name
        }
        return matchingSymbols.map { sym -> sym to findUsages(sym.qualifiedName) }
    }

    /** Get all symbols in a specific file */
    fun symbolsInFile(file: File): List<Symbol> = symbols.filter { it.file.absolutePath == file.absolutePath }

    /** Get usage count for each symbol, sorted descending */
    fun usageCounts(): List<Pair<Symbol, Int>> =
        symbols
            .filter { it.kind in setOf(SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.DATA_CLASS, SymbolKind.ENUM, SymbolKind.OBJECT) }
            .map { it to findUsages(it.qualifiedName).size }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

    /** Build a method-level call graph */
    fun callGraph(): List<MethodCall> {
        val calls = mutableListOf<MethodCall>()
        val methodSymbols = symbols.filter { it.kind == SymbolKind.FUNCTION }
        val methodsBySimpleName = methodSymbols.groupBy { it.name.substringAfterLast('.') }

        for (ref in references) {
            if (ref.kind != ReferenceKind.CALL) continue
            // Find the method being called
            val targets = methodsBySimpleName[ref.targetName] ?: continue
            // Find which method contains this call (the caller)
            val caller = findContainingMethod(ref.file, ref.line) ?: continue
            for (target in targets) {
                // Skip self-calls in the same location
                if (target.file == ref.file && target.line == ref.line) continue
                calls.add(MethodCall(caller = caller, target = target, file = ref.file, line = ref.line))
            }
        }
        return calls.distinctBy { "${it.caller.qualifiedName}->${it.target.qualifiedName}" }
    }

    private fun findContainingMethod(file: File, line: Int): Symbol? {
        val fileMethods = symbols.filter { it.file.absolutePath == file.absolutePath && it.kind == SymbolKind.FUNCTION }
        // Find the method whose line is closest to and before the reference line
        return fileMethods
            .filter { it.line <= line }
            .maxByOrNull { it.line }
    }

    companion object {
        fun build(sourceFiles: List<File>): SymbolIndex {
            val allSymbols = mutableListOf<Symbol>()
            val allRefs = mutableListOf<Reference>()

            val ktParser = KotlinPsiParser()
            val javaParser = JavaPsiParser()

            ktParser.use { parser ->
                for (file in sourceFiles) {
                    try {
                        when (file.extension) {
                            "kt" -> {
                                allSymbols.addAll(parser.extractDeclarations(file))
                                allRefs.addAll(parser.extractReferences(file))
                            }
                            "java" -> {
                                allSymbols.addAll(javaParser.extractDeclarations(file))
                                allRefs.addAll(javaParser.extractReferences(file))
                            }
                        }
                    } catch (e: Exception) {
                        // Skip files that fail to parse
                    }
                }
            }

            return SymbolIndex(allSymbols, allRefs)
        }
    }
}
