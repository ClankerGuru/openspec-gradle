package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class WorkflowTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.gradle")
            }
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
    }

    @Test
    fun `openspecPropose without name fails with clear message`() {
        val result = gradle("openspecPropose").buildAndFail()
        assertTrue(result.output.contains("Change name required"))
    }

    @Test
    fun `openspecArchive with non-existent change fails`() {
        val result = gradle("openspecArchive", "--name=does-not-exist").buildAndFail()
        assertTrue(result.output.contains("not found"))
    }

    @Test
    fun `openspecPropose on existing change fails`() {
        gradle("openspecPropose", "--name=dup").build()
        val result = gradle("openspecPropose", "--name=dup").buildAndFail()
        assertTrue(result.output.contains("already exists"))
    }

    @Test
    fun `openspecClean when no files exist succeeds`() {
        val result = gradle("openspecClean").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecClean")?.outcome)
        assertTrue(result.output.contains("Cleaned 0 generated files"))
    }

    @Test
    fun `openspecApply with no changes fails`() {
        val result = gradle("openspecApply").buildAndFail()
        assertTrue(result.output.contains("No changes found") || result.output.contains("not found"))
    }

    @Test
    fun `openspecApply auto-selects single change`() {
        gradle("openspecPropose", "--name=only-one").build()
        val result = gradle("openspecApply").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecApply")?.outcome)
        assertTrue(result.output.contains("only-one"))
    }

    @Test
    fun `openspecApply with multiple changes fails without name`() {
        gradle("openspecPropose", "--name=change-a").build()
        gradle("openspecPropose", "--name=change-b").build()
        val result = gradle("openspecApply").buildAndFail()
        assertTrue(result.output.contains("Multiple changes found"))
    }

    @Test
    fun `sync with agents=none generates no files`() {
        val result = gradle("openspecSync", "-Pzone.clanker.openspec.agents=none").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecSync")?.outcome)
        assertTrue(result.output.contains("No agents configured") || result.output.contains("Cleaned"))
    }

    @Test
    fun `sync removes files when agent is removed from config`() {
        // First sync with github + claude
        gradle("openspecSync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists(), "Copilot file should exist")
        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists(), "Claude file should exist")

        // Re-sync with only github — claude files should be removed
        val result = gradle("openspecSync", "-Pzone.clanker.openspec.agents=github").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists(), "Copilot file should still exist")
        assertFalse(File(testProjectDir, ".claude/commands/opsx/propose.md").exists(), "Claude file should be removed")
        assertTrue(result.output.contains("Removed") && result.output.contains("inactive tools"))
    }

    @Test
    fun `sync cleans empty parent directories after removing agent`() {
        gradle("openspecSync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertTrue(File(testProjectDir, ".claude/commands/opsx").exists())

        gradle("openspecSync", "-Pzone.clanker.openspec.agents=github").build()
        // The .claude/commands/opsx/ dir and parents should be pruned if empty
        assertFalse(File(testProjectDir, ".claude/commands/opsx").exists(), "opsx dir should be pruned")
    }

    @Test
    fun `full lifecycle - sync, propose, apply, archive, clean`() {
        gradle("openspecSync").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())

        gradle("openspecPropose", "--name=lifecycle-test").build()
        val changeDir = File(testProjectDir, "openspec/changes/lifecycle-test")
        assertTrue(changeDir.exists())
        assertTrue(File(changeDir, "proposal.md").exists())

        val applyResult = gradle("openspecApply", "--name=lifecycle-test").build()
        assertTrue(applyResult.output.contains("lifecycle-test"))

        gradle("openspecArchive", "--name=lifecycle-test").build()
        assertFalse(changeDir.exists())

        gradle("openspecClean").build()
        assertFalse(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
    }
}
