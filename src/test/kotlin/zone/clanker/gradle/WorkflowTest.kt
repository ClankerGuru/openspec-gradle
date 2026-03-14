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
        // Isolate from global ~/.gradle/gradle.properties agent config
        File(testProjectDir, "gradle.properties").writeText("zone.clanker.openspec.agents=github\n")
    }

    @Test
    fun `opsx-propose without name fails with clear message`() {
        val result = gradle("opsx-propose").buildAndFail()
        assertTrue(result.output.contains("Change name required"))
    }

    @Test
    fun `opsx-archive with non-existent change fails`() {
        val result = gradle("opsx-archive", "--name=does-not-exist").buildAndFail()
        assertTrue(result.output.contains("not found"))
    }

    @Test
    fun `opsx-propose on existing change fails`() {
        gradle("opsx-propose", "--name=dup").build()
        val result = gradle("opsx-propose", "--name=dup").buildAndFail()
        assertTrue(result.output.contains("already exists"))
    }

    @Test
    fun `opsx-clean when no files exist succeeds`() {
        val result = gradle("opsx-clean").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clean")?.outcome)
        assertTrue(result.output.contains("Cleaned 0 generated files"))
    }

    @Test
    fun `opsx-apply with no changes fails`() {
        val result = gradle("opsx-apply").buildAndFail()
        assertTrue(result.output.contains("No changes found") || result.output.contains("not found"))
    }

    @Test
    fun `opsx-apply auto-selects single change`() {
        gradle("opsx-propose", "--name=only-one").build()
        val result = gradle("opsx-apply").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-apply")?.outcome)
        assertTrue(result.output.contains("only-one"))
    }

    @Test
    fun `opsx-apply with multiple changes fails without name`() {
        gradle("opsx-propose", "--name=change-a").build()
        gradle("opsx-propose", "--name=change-b").build()
        val result = gradle("opsx-apply").buildAndFail()
        assertTrue(result.output.contains("Multiple changes found"))
    }

    @Test
    fun `sync with agents=none generates no files`() {
        val result = gradle("opsx-sync", "-Pzone.clanker.openspec.agents=none").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-sync")?.outcome)
        assertTrue(result.output.contains("No agents configured") || result.output.contains("Cleaned"))
    }

    @Test
    fun `full lifecycle - sync, propose, apply, archive, clean`() {
        gradle("opsx-sync").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())

        gradle("opsx-propose", "--name=lifecycle-test").build()
        val changeDir = File(testProjectDir, "openspec/changes/lifecycle-test")
        assertTrue(changeDir.exists())
        assertTrue(File(changeDir, "proposal.md").exists())

        val applyResult = gradle("opsx-apply", "--name=lifecycle-test").build()
        assertTrue(applyResult.output.contains("lifecycle-test"))

        gradle("opsx-archive", "--name=lifecycle-test").build()
        assertFalse(changeDir.exists())

        gradle("opsx-clean").build()
        assertFalse(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
    }
}
