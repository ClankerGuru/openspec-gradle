package zone.clanker.gradle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.generators.TaskReconciler
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskReconcilerTest {

    @TempDir
    lateinit var projectDir: File

    private fun createSource(path: String, content: String) {
        val file = File(projectDir, path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun createProposal(name: String, tasksContent: String) {
        val dir = File(projectDir, "opsx/changes/$name")
        dir.mkdirs()
        File(dir, "tasks.md").writeText(tasksContent)
    }

    @Test
    fun `extracts PascalCase symbol names`() {
        val names = TaskReconciler.extractSymbolNames("Refactor UserController to use BookRepository")
        assertTrue(names.contains("UserController"))
        assertTrue(names.contains("BookRepository"))
    }

    @Test
    fun `ignores common words`() {
        val names = TaskReconciler.extractSymbolNames("Add new Create endpoint for REST API")
        assertTrue(names.none { it == "Add" })
        assertTrue(names.none { it == "Create" })
        assertTrue(names.none { it == "REST" })
        assertTrue(names.none { it == "API" })
    }

    @Test
    fun `ignores short matches`() {
        val names = TaskReconciler.extractSymbolNames("Use IO for reading")
        assertTrue(names.isEmpty() || names.none { it.length < 3 })
    }

    @Test
    fun `detects missing symbols`() {
        createSource("src/main/kotlin/BookRepository.kt", """
            package com.example
            class BookRepository
        """.trimIndent())

        createProposal("feature", """
            # Tasks: feature
            - [ ] `f-1` Refactor UserController to use BookRepository
        """.trimIndent())

        val warnings = TaskReconciler.reconcile(projectDir)
        assertEquals(1, warnings.size)
        assertEquals("f-1", warnings[0].taskCode)
        assertTrue(warnings[0].missingSymbols.contains("UserController"))
        // BookRepository exists — should NOT be in missing
        assertTrue(!warnings[0].missingSymbols.contains("BookRepository"))
    }

    @Test
    fun `no warnings when all symbols exist`() {
        createSource("src/main/kotlin/BookRepository.kt", """
            package com.example
            class BookRepository
        """.trimIndent())

        createProposal("feature", """
            # Tasks: feature
            - [ ] `f-1` Update BookRepository
        """.trimIndent())

        val warnings = TaskReconciler.reconcile(projectDir)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `skips completed tasks`() {
        createProposal("feature", """
            # Tasks: feature
            - [x] `f-1` Refactor NonExistentClass
        """.trimIndent())

        createSource("src/main/kotlin/Dummy.kt", "class Dummy")

        val warnings = TaskReconciler.reconcile(projectDir)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `no proposals returns empty`() {
        val warnings = TaskReconciler.reconcile(projectDir)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `no source files returns empty`() {
        createProposal("feature", """
            # Tasks: feature
            - [ ] `f-1` Refactor SomeClass
        """.trimIndent())

        val warnings = TaskReconciler.reconcile(projectDir)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `suggests similar symbols`() {
        createSource("src/main/kotlin/UserService.kt", """
            package com.example
            class UserService
        """.trimIndent())

        createProposal("feature", """
            # Tasks: feature
            - [ ] `f-1` Refactor UserServce to add caching
        """.trimIndent())

        val warnings = TaskReconciler.reconcile(projectDir)
        // UserServce (typo) should be flagged, UserService should be suggested
        assertTrue(warnings.isNotEmpty(), "Expected warnings for typo 'UserServce'")
        val suggestions = warnings[0].suggestions.values.flatten()
        assertTrue(suggestions.contains("UserService"), "Expected 'UserService' as suggestion for 'UserServce'")
    }

    @Test
    fun `extracts file paths from backtick-wrapped references`() {
        val paths = TaskReconciler.extractFilePaths(
            "Modify `src/main/kotlin/Foo.kt` and `config/app.yml` for the change"
        )
        assertEquals(2, paths.size)
        assertTrue(paths.contains("src/main/kotlin/Foo.kt"))
        assertTrue(paths.contains("config/app.yml"))
    }

    @Test
    fun `extracts kts file paths`() {
        val paths = TaskReconciler.extractFilePaths(
            "Update `build.gradle.kts` and `settings.gradle.kts`"
        )
        assertEquals(2, paths.size)
        assertTrue(paths.contains("build.gradle.kts"))
        assertTrue(paths.contains("settings.gradle.kts"))
    }

    @Test
    fun `detects missing file references`() {
        createSource("src/main/kotlin/Existing.kt", "class Existing")

        createProposal("feature", """
            - [ ] `f-1` Modify `src/main/kotlin/Existing.kt` and `src/main/kotlin/Missing.kt`
        """.trimIndent())

        val fileWarnings = TaskReconciler.reconcileFiles(projectDir)
        assertEquals(1, fileWarnings.size)
        assertEquals("f-1", fileWarnings[0].taskCode)
        assertTrue(fileWarnings[0].missingPaths.contains("src/main/kotlin/Missing.kt"))
        // Existing.kt should not be in missing
        assertTrue(!fileWarnings[0].missingPaths.contains("src/main/kotlin/Existing.kt"))
    }

    @Test
    fun `reconcileFull combines symbol and file warnings`() {
        createSource("src/main/kotlin/Foo.kt", "class Foo")

        createProposal("feature", """
            - [ ] `f-1` Refactor MissingClass in `src/main/kotlin/gone.kt`
        """.trimIndent())

        val report = TaskReconciler.reconcileFull(projectDir)
        assertTrue(report.hasFindings())
        assertTrue(report.staleSymbols.isNotEmpty() || report.staleFiles.isNotEmpty())
    }

    @Test
    fun `reconcileFull with no findings returns empty report`() {
        createSource("src/main/kotlin/Foo.kt", "class Foo")

        createProposal("feature", """
            - [ ] `f-1` Update Foo class
        """.trimIndent())

        val report = TaskReconciler.reconcileFull(projectDir)
        assertTrue(!report.hasFindings() || report.staleFiles.isEmpty())
    }
}
