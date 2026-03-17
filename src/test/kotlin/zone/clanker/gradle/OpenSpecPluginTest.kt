package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        // Isolate from global ~/.gradle/gradle.properties agent config
        File(testProjectDir, "gradle.properties").writeText("zone.clanker.openspec.agents=github\n")
    }

    @Test
    fun `default config generates github-copilot files only`() {
        val result = gradle("opsx-sync").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-sync")?.outcome)

        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())
        assertTrue(!File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
    }

    @Test
    fun `openspec agents=github,claude generates for both`() {
        val result = gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-sync")?.outcome)

        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
    }

    @Test
    fun `openspec agents=claude generates only claude files`() {
        val result = gradle("opsx-sync", "-Pzone.clanker.openspec.agents=claude").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-sync")?.outcome)

        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
        assertTrue(!File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
    }

    @Test
    fun `openspec agents=none cleans existing files`() {
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(File(testProjectDir, ".claude/commands/opsx/propose.md").exists())

        val result = gradle("opsx-sync", "-Pzone.clanker.openspec.agents=none").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-sync")?.outcome)

        assertTrue(!File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
        assertTrue(!File(testProjectDir, ".claude/commands/opsx/propose.md").exists())
    }

    @Test
    fun `opsx-propose creates change directory`() {
        val result = gradle("opsx-propose", "--name=test-change").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-propose")?.outcome)
        assertTrue(File(testProjectDir, "opsx/changes/test-change/proposal.md").exists())
        assertTrue(File(testProjectDir, "opsx/changes/test-change/design.md").exists())
        assertTrue(File(testProjectDir, "opsx/changes/test-change/tasks.md").exists())
    }

    @Test
    fun `opsx-clean removes generated files`() {
        gradle("opsx-sync").build()
        assertTrue(File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())

        val result = gradle("opsx-clean").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clean")?.outcome)
        assertTrue(!File(testProjectDir, ".github/prompts/opsx-propose.prompt.md").exists())
    }

    @Test
    fun `opsx-sync updates global gitignore`() {
        gradle("opsx-sync").build()
        val globalGitignore = zone.clanker.gradle.generators.GlobalGitignore.resolveGlobalGitignoreFile()
        assertTrue(globalGitignore.exists(), "Global gitignore should be created")
        val content = globalGitignore.readText()
        assertTrue(content.contains(".opsx/"), "Should contain .opsx/ pattern")
        assertTrue(content.contains(".github/prompts/opsx-*"), "Should contain copilot pattern")
        assertTrue(content.contains(".claude/commands/opsx/"), "Should contain claude pattern")
    }

    @Test
    fun `global gitignore is idempotent`() {
        gradle("opsx-sync").build()
        val globalGitignore = zone.clanker.gradle.generators.GlobalGitignore.resolveGlobalGitignoreFile()
        val first = globalGitignore.readText()
        gradle("opsx-sync").build()
        val second = globalGitignore.readText()
        assertEquals(first, second)
    }

    @Test
    fun `opsx-sync does not modify project gitignore`() {
        gradle("opsx-sync").build()
        val projectGitignore = File(testProjectDir, ".gitignore")
        if (projectGitignore.exists()) {
            assertFalse(projectGitignore.readText().contains("opsx-"), "Should not add entries to project .gitignore")
        }
    }

    @Test
    fun `tasks are in opsx group`() {
        val result = gradle("tasks", "--group=opsx").build()
        val output = result.output
        assertTrue(output.contains("opsx-sync"))
        assertTrue(output.contains("opsx-propose"))
        assertTrue(output.contains("opsx-apply"))
        assertTrue(output.contains("opsx-archive"))
        assertTrue(output.contains("opsx-clean"))
    }
}
