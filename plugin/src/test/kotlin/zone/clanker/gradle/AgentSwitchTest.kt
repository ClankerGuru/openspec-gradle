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

class AgentSwitchTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)
        .forwardOutput()

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
    fun `switch from claude to github removes claude files`() {
        // Generate claude files
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=claude").build()
        assertTrue(File(testProjectDir, ".claude/skills/opsx-propose/SKILL.md").exists())

        // Switch to github
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github").build()

        // Claude files should be gone
        assertFalse(File(testProjectDir, ".claude/skills/opsx-propose/SKILL.md").exists())
        // Claude directory should be pruned (empty)
        assertFalse(File(testProjectDir, ".claude").exists(), ".claude/ dir should be pruned when empty")
        // GitHub files should exist
        assertTrue(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())
    }

    @Test
    fun `switch from github to claude removes github files`() {
        // Generate github files
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github").build()
        assertTrue(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())

        // Switch to claude
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=claude").build()

        // GitHub skills should be gone
        assertFalse(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())
        // Claude files should exist
        assertTrue(File(testProjectDir, ".claude/skills/opsx-propose/SKILL.md").exists())
    }

    @Test
    fun `github dir preserved when it has non-opsx content`() {
        // Generate github files
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github").build()
        assertTrue(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())

        // Add non-opsx content
        File(testProjectDir, ".github/workflows").mkdirs()
        File(testProjectDir, ".github/workflows/ci.yml").writeText("name: CI")

        // Switch to claude
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=claude").build()

        // .github/ should still exist because of workflows/
        assertTrue(File(testProjectDir, ".github").exists(), ".github/ should be preserved with non-opsx content")
        assertTrue(File(testProjectDir, ".github/workflows/ci.yml").exists(), "ci.yml should be untouched")
        // But opsx skills should be gone
        assertFalse(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())
    }

    @Test
    fun `claude instructions file preserved when it has user content`() {
        // Generate claude files
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=claude").build()
        val claudeMd = File(testProjectDir, ".claude/CLAUDE.md")
        assertTrue(claudeMd.exists())

        // Add user content before the OPSX markers
        val content = claudeMd.readText()
        claudeMd.writeText("# My Project Rules\n\nAlways use tabs.\n\n$content")

        // Switch to github
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github").build()

        // CLAUDE.md should still exist with user content, OPSX section stripped
        assertTrue(claudeMd.exists(), "CLAUDE.md should be preserved with user content")
        val cleaned = claudeMd.readText()
        assertTrue(cleaned.contains("Always use tabs"), "User content should be preserved")
        assertFalse(cleaned.contains("OPSX:BEGIN"), "OPSX markers should be stripped")
    }

    @Test
    fun `clean task removes all agents regardless of config`() {
        // Generate for both
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=github,claude").build()
        assertTrue(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())
        assertTrue(File(testProjectDir, ".claude/skills/opsx-propose/SKILL.md").exists())

        // Clean with only github configured — should still clean both
        val result = gradle("opsx-clean", "-Pzone.clanker.openspec.agents=github").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clean")?.outcome)
        assertFalse(File(testProjectDir, ".github/skills/opsx-propose/SKILL.md").exists())
        assertFalse(File(testProjectDir, ".claude/skills/opsx-propose/SKILL.md").exists())
    }

    @Test
    fun `empty parent dirs pruned but project root never deleted`() {
        // Generate claude files
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=claude").build()
        assertTrue(File(testProjectDir, ".claude/skills/opsx-propose/SKILL.md").exists())

        // Switch to none
        gradle("opsx-sync", "-Pzone.clanker.openspec.agents=none").build()

        // .claude/ should be fully pruned
        assertFalse(File(testProjectDir, ".claude").exists(), ".claude/ should be pruned")
        // Project root should still exist
        assertTrue(testProjectDir.exists(), "Project root should never be deleted")
    }
}
