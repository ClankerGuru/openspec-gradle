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

        // Pre-compute: for each file, which class qualified names are imported/referenced
        val fileImportedClasses = references
            .filter { it.kind == ReferenceKind.IMPORT }
            .groupBy { it.file.absolutePath }
            .mapValues { (_, refs) -> refs.mapNotNull { it.targetQualifiedName }.toSet() }

        // Pre-compute: receiver type map per file — map property names to their type's qualified name
        // e.g., "repository" -> "com.example.BookRepository" from constructor params like (private val repository: BookRepository)
        val fileReceiverTypes = buildReceiverTypeMap()

        for (ref in references) {
            if (ref.kind != ReferenceKind.CALL) continue
            val targets = methodsBySimpleName[ref.targetName] ?: continue
            val caller = findContainingMethod(ref.file, ref.line) ?: continue

            // Try to extract the receiver from context (e.g., "repository.searchBooks(query)" -> "repository")
            val receiver = extractReceiver(ref.context, ref.targetName)
            val callerClassQN = caller.qualifiedName.substringBeforeLast('.', "")

            // Resolve which target is actually being called
            val resolvedTargets = if (targets.size == 1) {
                targets // Only one candidate — no ambiguity
            } else {
                disambiguateTargets(targets, ref, caller, receiver, callerClassQN, fileImportedClasses, fileReceiverTypes)
            }

            for (target in resolvedTargets) {
                if (target.file == ref.file && target.line == ref.line) continue
                // Skip only true self-calls (same method calling itself)
                val targetClassQN = target.qualifiedName.substringBeforeLast('.', "")
                if (targetClassQN == callerClassQN && target.qualifiedName == caller.qualifiedName) continue
                calls.add(MethodCall(caller = caller, target = target, file = ref.file, line = ref.line))
            }
        }
        return calls.distinctBy { "${it.caller.qualifiedName}->${it.target.qualifiedName}" }
    }

    /**
     * Extract receiver name from a call context string.
     * e.g., "repository.searchBooks(query)" -> "repository"
     * e.g., "val result = api.fetch()" -> "api"
     */
    private fun extractReceiver(context: String, methodName: String): String? {
        val pattern = Regex("""(\w+)\.$methodName\s*\(""")
        val match = pattern.find(context) ?: return null
        val receiver = match.groupValues[1]
        // Skip common non-receiver prefixes
        if (receiver in setOf("this", "super", "it", "Companion")) return null
        return receiver
    }

    /**
     * Build a map: file path -> (property name -> class qualified name)
     * from constructor parameters and property declarations with type annotations.
     * Accumulates across all classes in a file.
     */
    private fun buildReceiverTypeMap(): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()

        for (symbol in symbols) {
            if (symbol.kind !in setOf(SymbolKind.CLASS, SymbolKind.DATA_CLASS)) continue
            val filePath = symbol.file.absolutePath
            val fileRefs = references.filter { it.file.absolutePath == filePath }
            val imports = fileRefs.filter { it.kind == ReferenceKind.IMPORT }
                .mapNotNull { ref -> ref.targetQualifiedName?.let { ref.targetName to it } }
                .toMap()

            try {
                val lines = symbol.file.readLines()
                val classLine = if (symbol.line - 1 in lines.indices) symbol.line - 1 else continue
                val searchRange = classLine until minOf(classLine + 15, lines.size)
                val paramPattern = Regex("""(?:val|var)\s+(\w+)\s*:\s*(\w+)""")

                for (lineIdx in searchRange) {
                    val line = lines[lineIdx]
                    paramPattern.findAll(line).forEach { match ->
                        val propName = match.groupValues[1]
                        val typeName = match.groupValues[2]
                        // Resolve: imports -> same-package -> unique symbol match
                        val qualifiedType = imports[typeName]
                            ?: bySimpleName[typeName]?.singleOrNull()?.qualifiedName
                            ?: if (symbol.packageName.isNotEmpty()) "${symbol.packageName}.$typeName" else null
                        if (qualifiedType != null) {
                            result.getOrPut(filePath) { mutableMapOf() }[propName] = qualifiedType
                        }
                    }
                    if (line.contains("{") && lineIdx > classLine) break
                }
            } catch (_: Exception) { }
        }
        return result
    }

    /**
     * Disambiguate multiple method targets with the same simple name.
     */
    private fun disambiguateTargets(
        targets: List<Symbol>,
        ref: Reference,
        caller: Symbol,
        receiver: String?,
        callerClassQN: String,
        fileImportedClasses: Map<String, Set<String>>,
        fileReceiverTypes: Map<String, Map<String, String>>,
    ): List<Symbol> {
        val filePath = ref.file.absolutePath

        // Strategy 1: If we have a receiver name, look up its type
        if (receiver != null) {
            val receiverTypes = fileReceiverTypes[filePath]
            val receiverQN = receiverTypes?.get(receiver)
            if (receiverQN != null) {
                val matched = targets.filter { it.qualifiedName.startsWith("$receiverQN.") }
                if (matched.isNotEmpty()) return matched
            }
        }

        // Strategy 2: Filter to targets whose containing class is imported in the caller's file
        val imports = fileImportedClasses[filePath] ?: emptySet()
        val importFiltered = targets.filter { target ->
            val targetClassQN = target.qualifiedName.substringBeforeLast('.', "")
            targetClassQN in imports || targetClassQN == callerClassQN
        }
        if (importFiltered.isNotEmpty() && importFiltered.size < targets.size) return importFiltered

        // Strategy 3: Prefer targets in the same package as the caller
        val callerPkg = caller.packageName
        val samePackage = targets.filter { it.packageName == callerPkg }
        if (samePackage.isNotEmpty() && samePackage.size < targets.size) return samePackage

        // No disambiguation possible — return all (will produce some false edges)
        return targets
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

            KotlinPsiParser().use { ktParser ->
                for (file in sourceFiles) {
                    try {
                        when (file.extension) {
                            "kt" -> {
                                allSymbols.addAll(ktParser.extractDeclarations(file))
                                allRefs.addAll(ktParser.extractReferences(file))
                            }
                            "java" -> {
                                val javaParser = JavaPsiParser()
                                allSymbols.addAll(javaParser.extractDeclarations(file))
                                allRefs.addAll(javaParser.extractReferences(file))
                            }
                        }
                    } catch (e: Exception) {
                        System.err.println("Warning: Failed to parse ${file.name}: ${e.message}")
                    }
                }
            }

            return SymbolIndex(allSymbols, allRefs)
        }
    }
}
