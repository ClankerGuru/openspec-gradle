package zone.clanker.gradle.psi

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import java.io.File

/**
 * Parses Java source files and extracts symbols + references.
 */
class JavaPsiParser {

    fun extractDeclarations(file: File): List<Symbol> {
        val cu = StaticJavaParser.parse(file)
        val packageName = cu.packageDeclaration.map { it.nameAsString }.orElse("")
        val symbols = mutableListOf<Symbol>()

        cu.findAll(ClassOrInterfaceDeclaration::class.java).forEach { cls ->
            val qn = if (packageName.isNotEmpty()) "$packageName.${cls.nameAsString}" else cls.nameAsString
            symbols.add(Symbol(
                name = cls.nameAsString,
                qualifiedName = qn,
                kind = if (cls.isInterface) SymbolKind.INTERFACE else SymbolKind.CLASS,
                file = file,
                line = cls.begin.map { it.line }.orElse(0),
                packageName = packageName,
            ))

            cls.methods.forEach { method ->
                symbols.add(Symbol(
                    name = "${cls.nameAsString}.${method.nameAsString}",
                    qualifiedName = "$qn.${method.nameAsString}",
                    kind = SymbolKind.FUNCTION,
                    file = file,
                    line = method.begin.map { it.line }.orElse(0),
                    packageName = packageName,
                ))
            }

            cls.fields.forEach { field ->
                field.variables.forEach { variable ->
                    symbols.add(Symbol(
                        name = "${cls.nameAsString}.${variable.nameAsString}",
                        qualifiedName = "$qn.${variable.nameAsString}",
                        kind = SymbolKind.PROPERTY,
                        file = file,
                        line = field.begin.map { it.line }.orElse(0),
                        packageName = packageName,
                    ))
                }
            }
        }

        cu.findAll(EnumDeclaration::class.java).forEach { en ->
            val qn = if (packageName.isNotEmpty()) "$packageName.${en.nameAsString}" else en.nameAsString
            symbols.add(Symbol(
                name = en.nameAsString,
                qualifiedName = qn,
                kind = SymbolKind.ENUM,
                file = file,
                line = en.begin.map { it.line }.orElse(0),
                packageName = packageName,
            ))
        }

        return symbols
    }

    fun extractReferences(file: File): List<Reference> {
        val cu = StaticJavaParser.parse(file)
        val refs = mutableListOf<Reference>()

        // Imports
        cu.imports.forEach { import ->
            val name = import.nameAsString
            refs.add(Reference(
                targetName = name.substringAfterLast('.'),
                targetQualifiedName = name,
                kind = ReferenceKind.IMPORT,
                file = file,
                line = import.begin.map { it.line }.orElse(0),
                context = import.toString().trim(),
            ))
        }

        // Extends / Implements
        cu.findAll(ClassOrInterfaceDeclaration::class.java).forEach { cls ->
            cls.extendedTypes.forEach { ext ->
                refs.add(Reference(
                    targetName = ext.nameAsString,
                    targetQualifiedName = null,
                    kind = ReferenceKind.SUPERTYPE,
                    file = file,
                    line = ext.begin.map { it.line }.orElse(0),
                    context = "extends ${ext.nameAsString}",
                ))
            }
            cls.implementedTypes.forEach { impl ->
                refs.add(Reference(
                    targetName = impl.nameAsString,
                    targetQualifiedName = null,
                    kind = ReferenceKind.SUPERTYPE,
                    file = file,
                    line = impl.begin.map { it.line }.orElse(0),
                    context = "implements ${impl.nameAsString}",
                ))
            }
        }

        // Method calls
        cu.accept(object : VoidVisitorAdapter<Void?>() {
            override fun visit(n: MethodCallExpr, arg: Void?) {
                refs.add(Reference(
                    targetName = n.nameAsString,
                    targetQualifiedName = null,
                    kind = ReferenceKind.CALL,
                    file = file,
                    line = n.begin.map { it.line }.orElse(0),
                    context = n.toString().take(80),
                ))
                super.visit(n, arg)
            }
        }, null)

        return refs
    }
}
