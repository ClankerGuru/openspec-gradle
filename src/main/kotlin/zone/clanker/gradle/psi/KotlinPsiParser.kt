package zone.clanker.gradle.psi

import java.io.File

/**
 * Parses Kotlin source files and extracts symbols + references.
 * Uses robust regex/text parsing — no PSI classloader needed at runtime.
 */
class KotlinPsiParser : AutoCloseable {

    fun extractDeclarations(file: File): List<Symbol> = KotlinRegexParser.extractDeclarations(file)

    fun extractReferences(file: File): List<Reference> = KotlinRegexParser.extractReferences(file)

    override fun close() {}
}

/**
 * Thorough regex-based Kotlin parser that extracts declarations and references
 * with line numbers. Works without kotlin-compiler-embeddable at runtime.
 */
object KotlinRegexParser {

    private val PACKAGE_RE = Regex("""^package\s+([\w.]+)""")
    private val IMPORT_RE = Regex("""^import\s+([\w.]+)(?:\s+as\s+\w+)?""")
    private val CLASS_RE = Regex("""(?:^|\s)((?:data|sealed|abstract|open|inner|enum|annotation)\s+)*class\s+(\w+)""")
    private val INTERFACE_RE = Regex("""(?:^|\s)(?:sealed\s+|fun\s+)?interface\s+(\w+)""")
    private val OBJECT_RE = Regex("""(?:^|\s)(?:companion\s+)?object\s+(\w+)""")
    private val FUN_RE = Regex("""(?:^|\s)(?:override\s+|suspend\s+|inline\s+|private\s+|internal\s+|protected\s+|public\s+|operator\s+|infix\s+)*fun\s+(?:<[^>]+>\s+)?(\w+)\s*\(""")
    private val PROP_RE = Regex("""(?:^|\s)(?:override\s+|private\s+|internal\s+|protected\s+|public\s+|lateinit\s+|const\s+)*(?:val|var)\s+(\w+)\s*[=:]""")
    private val CALL_RE = Regex("""(\w+)\s*\(""")
    private val TYPE_REF_RE = Regex(""":[\s]*(\w+)""")
    private val SUPERTYPE_RE = Regex("""(?:class|interface|object)\s+\w+(?:<[^>]*>)?\s*(?:\([^)]*\))?\s*:\s*(.+?)(?:\{|$)""")

    fun extractDeclarations(file: File): List<Symbol> {
        val lines = file.readLines()
        val symbols = mutableListOf<Symbol>()
        var packageName = ""
        var currentClass: String? = null
        var currentClassQN: String? = null

        for ((idx, line) in lines.withIndex()) {
            val lineNum = idx + 1
            val trimmed = line.trim()

            // Skip comments
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue

            PACKAGE_RE.find(trimmed)?.let { packageName = it.groupValues[1] }

            // Classes
            CLASS_RE.find(trimmed)?.let { match ->
                val modifiers = match.groupValues[1].trim()
                val name = match.groupValues[2]
                if (name == name.replaceFirstChar { it.uppercase() }) { // skip lowercase (local vars)
                    val qn = if (packageName.isNotEmpty()) "$packageName.$name" else name
                    val kind = when {
                        modifiers.contains("data") -> SymbolKind.DATA_CLASS
                        modifiers.contains("enum") -> SymbolKind.ENUM
                        else -> SymbolKind.CLASS
                    }
                    symbols.add(Symbol(name, qn, kind, file, lineNum, packageName))
                    currentClass = name
                    currentClassQN = qn
                }
            }

            // Interfaces
            INTERFACE_RE.find(trimmed)?.let { match ->
                val name = match.groupValues[1]
                val qn = if (packageName.isNotEmpty()) "$packageName.$name" else name
                symbols.add(Symbol(name, qn, SymbolKind.INTERFACE, file, lineNum, packageName))
                currentClass = name
                currentClassQN = qn
            }

            // Objects
            OBJECT_RE.find(trimmed)?.let { match ->
                val name = match.groupValues[1]
                if (name.isNotEmpty() && name != "companion") {
                    val qn = if (packageName.isNotEmpty()) "$packageName.$name" else name
                    symbols.add(Symbol(name, qn, SymbolKind.OBJECT, file, lineNum, packageName))
                    currentClass = name
                    currentClassQN = qn
                }
            }

            // Functions
            FUN_RE.find(trimmed)?.let { match ->
                val name = match.groupValues[1]
                val prefix = if (currentClass != null) "$currentClass." else ""
                val qnPrefix = currentClassQN ?: packageName
                val qn = if (qnPrefix.isNotEmpty()) "$qnPrefix.$name" else name
                symbols.add(Symbol("$prefix$name", qn, SymbolKind.FUNCTION, file, lineNum, packageName))
            }

            // Properties (top-level or class-level)
            if (!trimmed.startsWith("fun ") && !trimmed.startsWith("class ")) {
                PROP_RE.find(trimmed)?.let { match ->
                    val name = match.groupValues[1]
                    // Skip common local variable patterns
                    if (name != "it" && name != "this" && !name.startsWith("_")) {
                        val prefix = if (currentClass != null) "$currentClass." else ""
                        val qnPrefix = currentClassQN ?: packageName
                        val qn = if (qnPrefix.isNotEmpty()) "$qnPrefix.$name" else name
                        symbols.add(Symbol("$prefix$name", qn, SymbolKind.PROPERTY, file, lineNum, packageName))
                    }
                }
            }

            // Track class scope (simple brace counting)
            if (trimmed == "}" && !trimmed.contains("{")) {
                // Might be closing a class — simplified tracking
            }
        }

        return symbols
    }

    fun extractReferences(file: File): List<Reference> {
        val lines = file.readLines()
        val refs = mutableListOf<Reference>()
        var packageName = ""

        for ((idx, line) in lines.withIndex()) {
            val lineNum = idx + 1
            val trimmed = line.trim()

            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue

            PACKAGE_RE.find(trimmed)?.let { packageName = it.groupValues[1] }

            // Imports
            IMPORT_RE.find(trimmed)?.let { match ->
                val fqName = match.groupValues[1]
                refs.add(Reference(
                    targetName = fqName.substringAfterLast('.'),
                    targetQualifiedName = fqName,
                    kind = ReferenceKind.IMPORT,
                    file = file,
                    line = lineNum,
                    context = trimmed,
                ))
            }

            // Supertypes
            SUPERTYPE_RE.find(trimmed)?.let { match ->
                val supertypes = match.groupValues[1]
                // Parse comma-separated supertypes, handling generics
                val types = splitSupertypes(supertypes)
                for (type in types) {
                    val name = type.trim().substringBefore('<').substringBefore('(').trim()
                    if (name.isNotEmpty() && name[0].isUpperCase()) {
                        refs.add(Reference(
                            targetName = name,
                            targetQualifiedName = null,
                            kind = ReferenceKind.SUPERTYPE,
                            file = file,
                            line = lineNum,
                            context = trimmed.take(80),
                        ))
                    }
                }
            }

            // Function calls (skip imports, package, keywords)
            if (!trimmed.startsWith("import ") && !trimmed.startsWith("package ")) {
                CALL_RE.findAll(trimmed).forEach { match ->
                    val name = match.groupValues[1]
                    // Skip keywords and lowercase builtins
                    if (name !in KOTLIN_KEYWORDS && name.isNotEmpty()) {
                        refs.add(Reference(
                            targetName = name,
                            targetQualifiedName = null,
                            kind = if (name[0].isUpperCase()) ReferenceKind.CONSTRUCTOR else ReferenceKind.CALL,
                            file = file,
                            line = lineNum,
                            context = trimmed.take(80),
                        ))
                    }
                }
            }

            // Type references (constructor params, return types, property types)
            if (!trimmed.startsWith("import ") && !trimmed.startsWith("package ")) {
                TYPE_REF_RE.findAll(trimmed).forEach { match ->
                    val name = match.groupValues[1]
                    if (name[0].isUpperCase() && name !in KOTLIN_BUILTIN_TYPES) {
                        refs.add(Reference(
                            targetName = name,
                            targetQualifiedName = null,
                            kind = ReferenceKind.TYPE_REF,
                            file = file,
                            line = lineNum,
                            context = trimmed.take(80),
                        ))
                    }
                }
            }
        }

        return refs
    }

    private fun splitSupertypes(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in text) {
            when {
                ch == '<' || ch == '(' -> { depth++; current.append(ch) }
                ch == '>' || ch == ')' -> { depth--; current.append(ch) }
                ch == ',' && depth == 0 -> { result.add(current.toString()); current.clear() }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private val KOTLIN_KEYWORDS = setOf(
        "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
        "return", "throw", "break", "continue", "in", "is", "as", "fun", "val",
        "var", "class", "interface", "object", "enum", "sealed", "data", "inner",
        "abstract", "open", "override", "private", "protected", "internal", "public",
        "suspend", "inline", "by", "get", "set", "init", "constructor", "companion",
        "import", "package", "typealias", "where", "super", "this",
    )

    private val KOTLIN_BUILTIN_TYPES = setOf(
        "String", "Int", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Char",
        "Unit", "Nothing", "Any", "Array", "List", "Set", "Map", "Pair", "Triple",
        "MutableList", "MutableSet", "MutableMap", "Sequence", "Iterable",
    )
}
