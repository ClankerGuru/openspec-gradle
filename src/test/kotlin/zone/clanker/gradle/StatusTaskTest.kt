package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StatusTaskTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        File(projectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.gradle")
            }
        """.trimIndent())
        File(projectDir, "build.gradle.kts").writeText("")
        File(projectDir, "gradle.properties").writeText("zone.clanker.openspec.agents=github\n")
    }

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*args)
        .withPluginClasspath()
        .forwardOutput()

    private fun createProposal(name: String, tasks: String) {
        val dir = File(projectDir, "opsx/changes/$name")
        dir.mkdirs()
        File(dir, "tasks.md").writeText(tasks)
    }

    private fun statusFile() = File(projectDir, ".opsx/status.md")

    @Test
    fun `opsx-status runs with no proposals`() {
        val result = gradle("opsx-status").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-status")?.outcome)
        val content = statusFile().readText()
        assertTrue(content.contains("No active proposals"))
    }

    @Test
    fun `opsx-status shows dashboard with proposals`() {
        createProposal("my-feature", """
            - [ ] `mf-1` First task
            - [x] `mf-2` Done task
            - [ ] `mf-3` Third task
        """.trimIndent())

        val result = gradle("opsx-status").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-status")?.outcome)
        val content = statusFile().readText()
        assertTrue(content.contains("my-feature"))
        assertTrue(content.contains("1/3"))
    }

    @Test
    fun `opsx-status with --proposal filter`() {
        createProposal("alpha", "- [ ] `a-1` Alpha task")
        createProposal("beta", "- [ ] `b-1` Beta task")

        val result = gradle("opsx-status", "--proposal=alpha").build()
        assertTrue(result.output.contains("alpha"))
        assertFalse(result.output.contains("beta"))
    }

    @Test
    fun `opsx-status shows multiple proposals with progress`() {
        createProposal("feature-a", """
            - [x] `fa-1` Done
            - [x] `fa-2` Done
        """.trimIndent())

        createProposal("feature-b", """
            - [ ] `fb-1` Todo
            - [ ] `fb-2` Todo
            - [ ] `fb-3` Todo
        """.trimIndent())

        val result = gradle("opsx-status").build()
        val content = statusFile().readText()
        assertTrue(content.contains("feature-a"))
        assertTrue(content.contains("feature-b"))
        assertTrue(content.contains("2/5 tasks done"), "Should show 2/5 tasks done")
    }

    @Test
    fun `dynamic task registration creates opsx tasks`() {
        createProposal("my-feature", """
            - [ ] `mf-1` First task
            - [ ] `mf-2` Second task
        """.trimIndent())

        val result = gradle("opsx-mf-1").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-mf-1")?.outcome)
        assertTrue(result.output.contains("mf-1"))
        assertTrue(result.output.contains("TODO"))
    }

    @Test
    fun `dynamic task --set=done updates status`() {
        createProposal("my-feature", """
            - [ ] `mf-1` First task
            - [ ] `mf-2` Second task
        """.trimIndent())

        val result = gradle("opsx-mf-1", "--set=done").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-mf-1")?.outcome)
        assertTrue(result.output.contains("DONE"))

        // Verify file was updated
        val content = File(projectDir, "opsx/changes/my-feature/tasks.md").readText()
        assertTrue(content.contains("[x] `mf-1`"))
        assertTrue(content.contains("[ ] `mf-2`")) // unchanged
    }

    @Test
    fun `dynamic task --set=progress updates status`() {
        createProposal("my-feature", "- [ ] `mf-1` A task")

        val result = gradle("opsx-mf-1", "--set=progress").build()
        assertTrue(result.output.contains("IN_PROGRESS"))

        val content = File(projectDir, "opsx/changes/my-feature/tasks.md").readText()
        assertTrue(content.contains("🔄 `mf-1`"), "Expected emoji IN_PROGRESS, got: $content")
    }

    @Test
    fun `dynamic task blocks on unmet dependencies`() {
        createProposal("my-feature", """
            - [ ] `mf-1` First task
            - [ ] `mf-2` Second task → depends: mf-1
        """.trimIndent())

        val result = gradle("opsx-mf-2", "--set=done").buildAndFail()
        assertTrue(result.output.contains("blocked"))
        assertTrue(result.output.contains("mf-1"))
    }

    @Test
    fun `dynamic task allows done when deps are met`() {
        createProposal("my-feature", """
            - [x] `mf-1` First task done
            - [ ] `mf-2` Second task → depends: mf-1
        """.trimIndent())

        val result = gradle("opsx-mf-2", "--set=done").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-mf-2")?.outcome)
    }

    @Test
    fun `dynamic task auto-propagates parent completion`() {
        createProposal("my-feature", """
            - [ ] `mf-1` Parent
              - [x] `mf-1.1` Child done
              - [ ] `mf-1.2` Last child
        """.trimIndent())

        gradle("opsx-mf-1.2", "--set=done").build()

        val content = File(projectDir, "opsx/changes/my-feature/tasks.md").readText()
        // Parent should be auto-completed since all children are done
        val lines = content.lines()
        assertTrue(lines.any { it.contains("[x]") && it.contains("`mf-1`") },
            "Parent mf-1 should be auto-completed. Content:\n$content")
    }
}
