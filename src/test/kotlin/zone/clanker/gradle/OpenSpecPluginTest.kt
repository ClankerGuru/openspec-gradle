package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSpecPluginTest {

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
    fun `default config generates github-copilot files only`() {
        val result = gradle("openspecSync").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecSync")?.outcome)

        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(File(testProjectDir, ".github/skills/openspec-propose/SKILL.md").exists())
        assertTrue(!File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
    }

    @Test
    fun `openspec agents=github,claude generates for both`() {
        val result = gradle("openspecSync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecSync")?.outcome)

        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
    }

    @Test
    fun `openspec agents=claude generates only claude files`() {
        val result = gradle("openspecSync", "-Pzone.clanker.openspec.agents=claude").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecSync")?.outcome)

        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
        assertTrue(!File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
    }

    @Test
    fun `openspec agents=none cleans existing files`() {
        gradle("openspecSync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists())

        val result = gradle("openspecSync", "-Pzone.clanker.openspec.agents=none").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecSync")?.outcome)

        assertTrue(!File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(!File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
    }

    @Test
    fun `openspecPropose creates change directory`() {
        val result = gradle("openspecPropose", "--name=test-change").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecPropose")?.outcome)
        assertTrue(File(testProjectDir, "openspec/changes/test-change/proposal.md").exists())
        assertTrue(File(testProjectDir, "openspec/changes/test-change/design.md").exists())
        assertTrue(File(testProjectDir, "openspec/changes/test-change/tasks.md").exists())
    }

    @Test
    fun `openspecClean removes generated files`() {
        gradle("openspecSync").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())

        val result = gradle("openspecClean").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":openspecClean")?.outcome)
        assertTrue(!File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
    }

    @Test
    fun `openspecSync creates gitignore`() {
        gradle("openspecSync").build()
        val gitignore = File(testProjectDir, ".gitignore")
        assertTrue(gitignore.exists())
        assertTrue(gitignore.readText().contains("/.github/prompts/opsx-propose.prompt.md"))
    }

    @Test
    fun `gitignore is idempotent`() {
        gradle("openspecSync").build()
        val first = File(testProjectDir, ".gitignore").readText()
        gradle("openspecSync").build()
        val second = File(testProjectDir, ".gitignore").readText()
        assertEquals(first, second)
    }

    @Test
    fun `tasks are in openspec group`() {
        val result = gradle("tasks", "--group=openspec").build()
        val output = result.output
        assertTrue(output.contains("openspecSync"))
        assertTrue(output.contains("openspecPropose"))
        assertTrue(output.contains("openspecApply"))
        assertTrue(output.contains("openspecArchive"))
        assertTrue(output.contains("openspecClean"))
    }
}
