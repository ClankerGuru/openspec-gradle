package zone.clanker.gradle.psi

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.psi.*
import zone.clanker.gradle.core.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PsiTest {

    @TempDir
    lateinit var tempDir: File

    private fun writeKt(name: String, content: String): File {
        val file = File(tempDir, "$name.kt")
        file.writeText(content)
        return file
    }

    private fun writeJava(name: String, content: String): File {
        val file = File(tempDir, "$name.java")
        file.writeText(content)
        return file
    }

    // ── Kotlin Parser ──

    @Test
    fun `extracts class declarations`() {
        val file = writeKt("Foo", """
            package com.example
            class Foo {
                fun bar() {}
                val baz: String = ""
            }
        """.trimIndent())
        val parser = KotlinPsiParser()
        val symbols = parser.extractDeclarations(file)
        assertTrue(symbols.any { it.name == "Foo" && it.kind == SymbolKind.CLASS })
        assertTrue(symbols.any { it.name == "Foo.bar" && it.kind == SymbolKind.FUNCTION })
        assertTrue(symbols.any { it.name == "Foo.baz" && it.kind == SymbolKind.PROPERTY })
    }

    @Test
    fun `extracts data class`() {
        val file = writeKt("Data", """
            package com.example
            data class Book(val id: String, val title: String)
        """.trimIndent())
        val symbols = KotlinPsiParser().extractDeclarations(file)
        assertTrue(symbols.any { it.name == "Book" && it.kind == SymbolKind.DATA_CLASS })
    }

    @Test
    fun `extracts interface`() {
        val file = writeKt("Iface", """
            package com.example
            interface Repository {
                fun findAll(): List<String>
            }
        """.trimIndent())
        val symbols = KotlinPsiParser().extractDeclarations(file)
        assertTrue(symbols.any { it.name == "Repository" && it.kind == SymbolKind.INTERFACE })
    }

    @Test
    fun `extracts object`() {
        val file = writeKt("Obj", """
            package com.example
            object Singleton {
                fun getInstance(): Singleton = this
            }
        """.trimIndent())
        val symbols = KotlinPsiParser().extractDeclarations(file)
        assertTrue(symbols.any { it.name == "Singleton" && it.kind == SymbolKind.OBJECT })
    }

    @Test
    fun `extracts import references`() {
        val file = writeKt("User", """
            package com.example
            import com.example.model.Book
            class BookService(val book: Book)
        """.trimIndent())
        val refs = KotlinPsiParser().extractReferences(file)
        assertTrue(refs.any { it.targetName == "Book" && it.kind == ReferenceKind.IMPORT })
    }

    @Test
    fun `extracts constructor calls`() {
        val file = writeKt("Builder", """
            package com.example
            import com.example.model.Book
            val book = Book("1", "title")
        """.trimIndent())
        val refs = KotlinPsiParser().extractReferences(file)
        assertTrue(refs.any { it.targetName == "Book" && it.kind == ReferenceKind.CONSTRUCTOR })
    }

    @Test
    fun `extracts supertype references`() {
        val file = writeKt("Impl", """
            package com.example
            class BookServiceImpl : BookService, Closeable {
                fun close() {}
            }
        """.trimIndent())
        val refs = KotlinPsiParser().extractReferences(file)
        assertTrue(refs.any { it.targetName == "BookService" && it.kind == ReferenceKind.SUPERTYPE })
        assertTrue(refs.any { it.targetName == "Closeable" && it.kind == ReferenceKind.SUPERTYPE })
    }

    // ── Java Parser ──

    @Test
    fun `extracts java class declarations`() {
        val file = writeJava("Foo", """
            package com.example;
            public class Foo {
                private String name;
                public void bar() {}
            }
        """.trimIndent())
        val symbols = JavaPsiParser().extractDeclarations(file)
        assertTrue(symbols.any { it.name == "Foo" && it.kind == SymbolKind.CLASS })
        assertTrue(symbols.any { it.name == "Foo.bar" && it.kind == SymbolKind.FUNCTION })
        assertTrue(symbols.any { it.name == "Foo.name" && it.kind == SymbolKind.PROPERTY })
    }

    @Test
    fun `extracts java interface`() {
        val file = writeJava("Repo", """
            package com.example;
            public interface Repository {
                void save(String item);
            }
        """.trimIndent())
        val symbols = JavaPsiParser().extractDeclarations(file)
        assertTrue(symbols.any { it.name == "Repository" && it.kind == SymbolKind.INTERFACE })
    }

    @Test
    fun `extracts java imports and supertypes`() {
        val file = writeJava("Impl", """
            package com.example;
            import java.io.Closeable;
            public class ServiceImpl implements Closeable {
                public void close() {}
            }
        """.trimIndent())
        val refs = JavaPsiParser().extractReferences(file)
        assertTrue(refs.any { it.targetName == "Closeable" && it.kind == ReferenceKind.IMPORT })
        assertTrue(refs.any { it.targetName == "Closeable" && it.kind == ReferenceKind.SUPERTYPE })
    }

    // ── Symbol Index ──

    @Test
    fun `builds index and finds usages`() {
        val model = writeKt("Book", """
            package com.example
            data class Book(val id: String, val title: String)
        """.trimIndent())
        val service = writeKt("BookService", """
            package com.example
            import com.example.Book
            class BookService {
                fun findBook(id: String): Book? = null
            }
        """.trimIndent())

        val index = SymbolIndex.build(listOf(model, service))
        val usages = index.findUsages("com.example.Book")
        assertTrue(usages.isNotEmpty(), "Book should have usages in BookService")
        assertTrue(usages.any { it.file == service }, "Should find usage in BookService file")
    }

    @Test
    fun `findUsagesByName works`() {
        val model = writeKt("Book", """
            package com.example
            data class Book(val id: String)
        """.trimIndent())
        val user = writeKt("User", """
            package com.example
            import com.example.Book
            val b = Book("1")
        """.trimIndent())

        val index = SymbolIndex.build(listOf(model, user))
        val results = index.findUsagesByName("Book")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().second.isNotEmpty())
    }

    @Test
    fun `usage counts sorted descending`() {
        val a = writeKt("A", "package p\nclass A")
        val b = writeKt("B", "package p\nimport p.A\nclass B(val a: A)")
        val c = writeKt("C", "package p\nimport p.A\nclass C(val a: A)")

        val index = SymbolIndex.build(listOf(a, b, c))
        val counts = index.usageCounts()
        if (counts.isNotEmpty()) {
            assertTrue(counts.first().second >= counts.last().second)
        }
    }

    // ── Renamer ──

    @Test
    fun `computes rename edits`() {
        val file = writeKt("OldName", """
            package com.example
            class OldName {
                fun doStuff() {}
            }
        """.trimIndent())
        val user = writeKt("User", """
            package com.example
            import com.example.OldName
            val x = OldName()
        """.trimIndent())

        val index = SymbolIndex.build(listOf(file, user))
        val renamer = Renamer(index)
        val edits = renamer.computeRename("OldName", "NewName")
        assertTrue(edits.isNotEmpty(), "Should produce rename edits")
        assertTrue(edits.any { it.kind == "declaration" })
    }

    @Test
    fun `dry run does not modify files`() {
        val file = writeKt("Keep", """
            package com.example
            class Keep
        """.trimIndent())
        val original = file.readText()

        val index = SymbolIndex.build(listOf(file))
        val renamer = Renamer(index)
        val edits = renamer.computeRename("Keep", "Changed")
        // Don't call applyRename — just verify edits exist
        assertTrue(edits.isNotEmpty())
        assertEquals(original, file.readText(), "File should not be modified by compute alone")
    }

    // ── Cross-language ──

    @Test
    fun `indexes kotlin and java together`() {
        val ktFile = writeKt("KtService", """
            package com.example
            class KtService
        """.trimIndent())
        val javaFile = writeJava("JavaService", """
            package com.example;
            public class JavaService {}
        """.trimIndent())

        val index = SymbolIndex.build(listOf(ktFile, javaFile))
        assertTrue(index.symbols.any { it.name == "KtService" })
        assertTrue(index.symbols.any { it.name == "JavaService" })
    }

    // ── Call Graph ──

    @Test
    fun `detects method calls`() {
        val repo = writeKt("Repo", """
            package com.example
            class Repo {
                fun findAll(): List<String> = emptyList()
            }
        """.trimIndent())
        val service = writeKt("Service", """
            package com.example
            class Service(val repo: Repo) {
                fun getAll() = repo.findAll()
            }
        """.trimIndent())

        val index = SymbolIndex.build(listOf(repo, service))
        val calls = index.callGraph()
        assertTrue(calls.any { it.target.name.contains("findAll") }, "Should detect findAll() call")
    }
}
