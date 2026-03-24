package zone.clanker.gradle.core

import java.io.File

data class Symbol(
    val name: String,
    val qualifiedName: String,
    val kind: SymbolKind,
    val file: File,
    val line: Int,
    val packageName: String,
)

enum class SymbolKind(val label: String) {
    CLASS("class"), INTERFACE("interface"), ENUM("enum"), DATA_CLASS("data class"),
    OBJECT("object"), FUNCTION("fun"), PROPERTY("val/var"),
}

data class Reference(
    val targetName: String,
    val targetQualifiedName: String?,
    val kind: ReferenceKind,
    val file: File,
    val line: Int,
    val context: String,
)

enum class ReferenceKind(val label: String) {
    IMPORT("import"), CALL("call"), NAME_REF("reference"), SUPERTYPE("extends/implements"),
    TYPE_REF("type"), CONSTRUCTOR("constructor"),
}

data class MethodCall(
    val caller: Symbol,
    val target: Symbol,
    val file: File,
    val line: Int,
)

/**
 * A machine-checkable assertion declared on a task via `> verify:` syntax.
 *
 * @param type Assertion type: symbol-exists, symbol-not-in, file-exists, file-changed, build-passes
 * @param argument The argument for the assertion (e.g., symbol name, file path). Empty for build-passes.
 */
data class VerifyAssertion(
    val type: String,
    val argument: String,
)
