package zone.clanker.gradle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.generators.TaskCommandGenerator
import zone.clanker.gradle.generators.TaskWarning
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskCommandGeneratorTest {

    @TempDir
    lateinit var buildDir: File

    @TempDir
    lateinit var projectDir: File

    private fun createProposal(name: String, tasksContent: String) {
        val dir = File(projectDir, "opsx/changes/$name")
        dir.mkdirs()
        File(dir, "tasks.md").writeText(tasksContent)
    }

    @Test
    fun `generates skills for each task in proposal`() {
        createProposal("my-feature", """
            # Tasks: my-feature
            - [ ] `mf-1` Create the thing
            - [ ] `mf-2` Test the thing
        """.trimIndent())

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"))
        assertEquals(2, files.size)
        assertTrue(files.any { it.relativePath.contains("opsx-mf-1") })
        assertTrue(files.any { it.relativePath.contains("opsx-mf-2") })
    }

    @Test
    fun `skill content includes proposal context`() {
        createProposal("auth", """
            # Tasks: auth
            - [ ] `auth-1` Build login page
        """.trimIndent())

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"))
        val content = files[0].file.readText()
        assertTrue(content.contains("opsx/changes/auth/proposal.md"))
        assertTrue(content.contains("opsx/changes/auth/design.md"))
        assertTrue(content.contains("opsx/changes/auth/tasks.md"))
    }

    @Test
    fun `skill includes completion instruction`() {
        createProposal("fix", """
            # Tasks: fix
            - [ ] `fix-1` Fix the bug
        """.trimIndent())

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"))
        val content = files[0].file.readText()
        assertTrue(content.contains("./gradlew opsx-fix-1 --set=done"))
    }

    @Test
    fun `skill includes dependencies`() {
        createProposal("deps", """
            # Tasks: deps
            - [x] `d-1` First thing
            - [ ] `d-2` Second thing → depends: d-1
        """.trimIndent())

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"))
        val d2 = files.first { it.relativePath.contains("opsx-d-2") }
        val content = d2.file.readText()
        assertTrue(content.contains("d-1"))
        assertTrue(content.contains("Dependencies"))
    }

    @Test
    fun `generates for multiple agents`() {
        createProposal("multi", """
            # Tasks: multi
            - [ ] `m-1` Do something
        """.trimIndent())

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude", "github-copilot"))
        assertEquals(2, files.size) // one per agent
    }

    @Test
    fun `no proposals returns empty`() {
        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"))
        assertTrue(files.isEmpty())
    }

    @Test
    fun `includes reconciliation warnings`() {
        createProposal("warn", """
            # Tasks: warn
            - [ ] `w-1` Refactor UserController
        """.trimIndent())

        val warnings = listOf(
            TaskWarning(
                taskCode = "w-1",
                proposalName = "warn",
                description = "Refactor UserController",
                missingSymbols = listOf("UserController"),
                suggestions = mapOf("UserController" to listOf("BookController"))
            )
        )

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"), warnings)
        val content = files[0].file.readText()
        assertTrue(content.contains("Reconciliation Warning"))
        assertTrue(content.contains("UserController"))
        assertTrue(content.contains("BookController"))
    }

    @Test
    fun `generates skill files at skill paths`() {
        createProposal("test", """
            # Tasks: test
            - [ ] `t-1` Do something
        """.trimIndent())

        val files = TaskCommandGenerator.generate(projectDir, buildDir, listOf("claude"))
        // Should use skill path format: .claude/skills/opsx-t-1/SKILL.md
        assertTrue(files[0].relativePath.contains("skills/opsx-t-1/SKILL.md"))
    }
}
