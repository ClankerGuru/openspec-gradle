package zone.clanker.gradle.arch

import java.io.File

/**
 * A source file with parsed metadata — imports, class name, annotations, supertypes.
 */
data class SourceFile(
    val file: File,
    val packageName: String,
    val qualifiedName: String,
    val simpleName: String,
    val imports: List<String>,
    val annotations: List<String>,
    val supertypes: List<String>,
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val isObject: Boolean,
    val isDataClass: Boolean,
    val language: Language,
    val lineCount: Int,
    val methods: List<String>,
) {
    enum class Language { KOTLIN, JAVA }
}

/**
 * Parse a Kotlin or Java source file into a [SourceFile].
 * Reads only the structural parts — package, imports, class declaration, annotations.
 * Does not compile anything.
 */
fun parseSourceFile(file: File): SourceFile? {
    if (!file.isFile) return null
    val lang = when (file.extension) {
        "kt" -> SourceFile.Language.KOTLIN
        "java" -> SourceFile.Language.JAVA
        else -> return null
    }

    val lines = file.readLines()
    val packageName = lines.firstOrNull { it.trimStart().startsWith("package ") }
        ?.trimStart()?.removePrefix("package ")?.trimEnd(';')?.trim() ?: ""

    val imports = lines
        .filter { it.trimStart().startsWith("import ") }
        .map { it.trimStart().removePrefix("import ").trimEnd(';').trim() }
        .filter { !it.startsWith("java.") && !it.startsWith("javax.") && !it.startsWith("kotlin.") && !it.startsWith("kotlinx.") }

    val annotations = mutableListOf<String>()
    val methods = mutableListOf<String>()
    var className = ""
    var isInterface = false
    var isAbstract = false
    var isObject = false
    var isDataClass = false
    var supertypes = listOf<String>()

    for (line in lines) {
        val trimmed = line.trim()

        // Annotations
        if (trimmed.startsWith("@") && !trimmed.startsWith("@file") && !trimmed.startsWith("@param") && !trimmed.startsWith("@return")) {
            val anno = trimmed.substringBefore("(").substringBefore(" ").removePrefix("@")
            annotations.add(anno)
        }

        // Class/interface/object declaration
        if (className.isEmpty()) {
            val classMatch = findClassDeclaration(trimmed, lang)
            if (classMatch != null) {
                className = classMatch.name
                isInterface = classMatch.isInterface
                isAbstract = classMatch.isAbstract
                isObject = classMatch.isObject
                isDataClass = classMatch.isDataClass
                supertypes = classMatch.supertypes
            }
        }

        // Method declarations (simplified)
        if (lang == SourceFile.Language.KOTLIN) {
            if (trimmed.startsWith("fun ") || trimmed.startsWith("suspend fun ") ||
                trimmed.startsWith("override fun ") || trimmed.startsWith("private fun ") ||
                trimmed.startsWith("internal fun ") || trimmed.startsWith("protected fun ")) {
                val methodName = extractKotlinMethodName(trimmed)
                if (methodName != null) methods.add(methodName)
            }
        } else {
            val javaMethodPattern = Regex("""^(public|private|protected|static|final|synchronized|abstract|override|\s)*(void|int|long|boolean|String|List|Map|Set|Optional|[A-Z]\w*(<.*>)?)\s+\w+\s*\(.*""")
            if (javaMethodPattern.containsMatchIn(trimmed)) {
                val methodName = extractJavaMethodName(trimmed)
                if (methodName != null) methods.add(methodName)
            }
        }
    }

    if (className.isEmpty()) {
        // Use filename as fallback
        className = file.nameWithoutExtension
    }

    val qualifiedName = if (packageName.isNotEmpty()) "$packageName.$className" else className

    return SourceFile(
        file = file,
        packageName = packageName,
        qualifiedName = qualifiedName,
        simpleName = className,
        imports = imports,
        annotations = annotations,
        supertypes = supertypes,
        isInterface = isInterface,
        isAbstract = isAbstract,
        isObject = isObject,
        isDataClass = isDataClass,
        language = lang,
        lineCount = lines.size,
        methods = methods,
    )
}

private data class ClassDeclaration(
    val name: String,
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val isObject: Boolean,
    val isDataClass: Boolean,
    val supertypes: List<String>,
)

private fun findClassDeclaration(line: String, lang: SourceFile.Language): ClassDeclaration? {
    // Skip comments
    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return null

    val keywords = line.split(" ", "\t").filter { it.isNotBlank() }
    val classIdx = keywords.indexOfFirst { it in setOf("class", "interface", "object", "enum") }
    if (classIdx < 0) return null

    val typeKeyword = keywords[classIdx]
    val nameToken = keywords.getOrNull(classIdx + 1)
        ?.substringBefore("(")?.substringBefore("{")?.substringBefore(":")?.substringBefore("<")
        ?.trim()
        ?: return null
    if (nameToken.isEmpty() || !nameToken[0].isUpperCase()) return null

    val isAbstract = "abstract" in keywords.subList(0, classIdx)
    val isData = "data" in keywords.subList(0, classIdx)

    // Extract supertypes from everything after ":" or "extends"/"implements"
    val supertypes = mutableListOf<String>()
    val afterName = line.substringAfter(nameToken, "")
    if (lang == SourceFile.Language.KOTLIN) {
        // Find the supertype colon — it comes after closing the constructor parens (or after the class name if no constructor)
        val afterConstructor = if (afterName.contains("(")) {
            // Skip past balanced parens
            var depth = 0
            var endIdx = 0
            for ((i, ch) in afterName.withIndex()) {
                if (ch == '(') depth++
                else if (ch == ')') { depth--; if (depth == 0) { endIdx = i + 1; break } }
            }
            afterName.substring(endIdx)
        } else afterName
        val colonPart = afterConstructor.substringAfter(":", "").substringBefore("{").trim()
        if (colonPart.isNotEmpty()) {
            colonPart.split(",").forEach { s ->
                val clean = s.trim().substringBefore("(").substringBefore("<").trim()
                if (clean.isNotEmpty() && clean[0].isUpperCase()) supertypes.add(clean)
            }
        }
    } else {
        val extendsPart = afterName.substringAfter("extends ", "").substringBefore("{").substringBefore("implements").trim()
        if (extendsPart.isNotEmpty()) {
            extendsPart.split(",").forEach { s ->
                val clean = s.trim().substringBefore("<").trim()
                if (clean.isNotEmpty() && clean[0].isUpperCase()) supertypes.add(clean)
            }
        }
        val implPart = afterName.substringAfter("implements ", "").substringBefore("{").trim()
        if (implPart.isNotEmpty()) {
            implPart.split(",").forEach { s ->
                val clean = s.trim().substringBefore("<").trim()
                if (clean.isNotEmpty() && clean[0].isUpperCase()) supertypes.add(clean)
            }
        }
    }

    return ClassDeclaration(
        name = nameToken,
        isInterface = typeKeyword == "interface",
        isAbstract = isAbstract,
        isObject = typeKeyword == "object",
        isDataClass = isData,
        supertypes = supertypes,
    )
}

private fun extractKotlinMethodName(line: String): String? {
    val funIdx = line.indexOf("fun ")
    if (funIdx < 0) return null
    val afterFun = line.substring(funIdx + 4).trim()
    // Skip extension receiver: Type.name(
    val name = afterFun.substringBefore("(").substringAfterLast(".").trim()
    return name.ifEmpty { null }
}

private fun extractJavaMethodName(line: String): String? {
    val parenIdx = line.indexOf('(')
    if (parenIdx < 0) return null
    val beforeParen = line.substring(0, parenIdx).trim()
    val name = beforeParen.substringAfterLast(" ").trim()
    return if (name.isNotEmpty() && name[0].isLowerCase()) name else null
}

/**
 * Scan source directories and parse all source files.
 */
fun scanSources(srcDirs: List<File>): List<SourceFile> =
    srcDirs
        .filter { it.exists() }
        .flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .mapNotNull { parseSourceFile(it) }
                .toList()
        }
