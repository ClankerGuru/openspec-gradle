package zone.clanker.gradle.tasks.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.core.VerifyAssertion
import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.core.Symbol
import zone.clanker.gradle.core.SymbolKind
import java.io.File

class AssertionRunnerTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `file-exists passes for existing file`() {
        val file = File(projectDir, "src/main/kotlin/Foo.kt")
        file.parentFile.mkdirs()
        file.writeText("class Foo")

        val results = AssertionRunner.run(
            listOf(VerifyAssertion("file-exists", "src/main/kotlin/Foo.kt")),
            projectDir,
        )

        assertEquals(1, results.size)
        assertTrue(results[0].passed, results[0].message)
    }

    @Test
    fun `file-exists fails for missing file`() {
        val results = AssertionRunner.run(
            listOf(VerifyAssertion("file-exists", "src/main/kotlin/Missing.kt")),
            projectDir,
        )

        assertEquals(1, results.size)
        assertFalse(results[0].passed)
        assertTrue(results[0].message.contains("not found"))
    }

    @Test
    fun `symbol-exists passes when symbol is in index`() {
        val index = SymbolIndex(
            symbols = listOf(
                Symbol("Foo", "com.example.Foo", SymbolKind.CLASS, File("Foo.kt"), 1, "com.example")
            ),
            references = emptyList(),
        )

        val results = AssertionRunner.run(
            listOf(VerifyAssertion("symbol-exists", "Foo")),
            projectDir,
            indexProvider = { index },
        )

        assertEquals(1, results.size)
        assertTrue(results[0].passed, results[0].message)
    }

    @Test
    fun `symbol-exists fails when symbol not in index`() {
        val index = SymbolIndex(
            symbols = emptyList(),
            references = emptyList(),
        )

        val results = AssertionRunner.run(
            listOf(VerifyAssertion("symbol-exists", "Bar")),
            projectDir,
            indexProvider = { index },
        )

        assertEquals(1, results.size)
        assertFalse(results[0].passed)
        assertTrue(results[0].message.contains("not found"))
    }

    @Test
    fun `symbol-not-in passes when symbol absent`() {
        val index = SymbolIndex(
            symbols = emptyList(),
            references = emptyList(),
        )

        val results = AssertionRunner.run(
            listOf(VerifyAssertion("symbol-not-in", "OldClass")),
            projectDir,
            indexProvider = { index },
        )

        assertEquals(1, results.size)
        assertTrue(results[0].passed, results[0].message)
    }

    @Test
    fun `symbol-not-in fails when symbol still exists`() {
        val index = SymbolIndex(
            symbols = listOf(
                Symbol("OldClass", "com.example.OldClass", SymbolKind.CLASS, File("OldClass.kt"), 1, "com.example")
            ),
            references = emptyList(),
        )

        val results = AssertionRunner.run(
            listOf(VerifyAssertion("symbol-not-in", "OldClass")),
            projectDir,
            indexProvider = { index },
        )

        assertEquals(1, results.size)
        assertFalse(results[0].passed)
        assertTrue(results[0].message.contains("still exists"))
    }

    @Test
    fun `multiple assertions all must pass`() {
        val file = File(projectDir, "src/main/kotlin/Alpha.kt")
        file.parentFile.mkdirs()
        file.writeText("class Alpha")

        val results = AssertionRunner.run(
            listOf(
                VerifyAssertion("file-exists", "src/main/kotlin/Alpha.kt"),
                VerifyAssertion("file-exists", "src/main/kotlin/Missing.kt"),
            ),
            projectDir,
        )

        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertFalse(results[1].passed)
    }

    @Test
    fun `unknown assertion type fails`() {
        val results = AssertionRunner.run(
            listOf(VerifyAssertion("invalid-type", "arg")),
            projectDir,
        )

        assertEquals(1, results.size)
        assertFalse(results[0].passed)
        assertTrue(results[0].message.contains("Unknown assertion type"))
    }

    @Test
    fun `formatFailures produces readable output`() {
        val failures = listOf(
            AssertionRunner.AssertionResult(
                VerifyAssertion("file-exists", "src/Foo.kt"),
                false,
                "File 'src/Foo.kt' not found"
            ),
            AssertionRunner.AssertionResult(
                VerifyAssertion("symbol-exists", "Bar"),
                false,
                "Symbol 'Bar' not found in source"
            ),
        )

        val output = AssertionRunner.formatFailures("tlv-3", failures)
        assertTrue(output.contains("tlv-3"))
        assertTrue(output.contains("file-exists"))
        assertTrue(output.contains("symbol-exists"))
        assertTrue(output.contains("--force"))
    }
}
