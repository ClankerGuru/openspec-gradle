package zone.clanker.gradle

import zone.clanker.gradle.generators.CommandGenerator
import zone.clanker.gradle.generators.SkillGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {

    @TempDir
    lateinit var buildDir: File

    // ── SkillGenerator ──────────────────────────────────

    @Test
    fun `SkillGenerator generates 17 skills per tool`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude"))
        assertEquals(17, files.size)
        files.forEach { assertTrue(it.file.exists()) }
    }

    @Test
    fun `SkillGenerator generates for multiple tools`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude", "github-copilot"))
        assertEquals(34, files.size) // 17 skills * 2 tools
    }

    @Test
    fun `SkillGenerator with no tools generates nothing`() {
        val files = SkillGenerator.generate(buildDir, emptyList())
        assertEquals(0, files.size)
    }

    @Test
    fun `SkillGenerator with invalid tool generates nothing`() {
        val files = SkillGenerator.generate(buildDir, listOf("vim"))
        assertEquals(0, files.size)
    }

    @Test
    fun `SkillGenerator files have non-empty content`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude"))
        files.forEach {
            assertTrue(it.file.readText().length > 50, "File ${it.relativePath} is too short")
        }
    }

    // ── CommandGenerator ────────────────────────────────

    @Test
    fun `CommandGenerator generates 17 commands per tool`() {
        val files = CommandGenerator.generate(buildDir, listOf("claude"))
        assertEquals(17, files.size)
    }

    @Test
    fun `CommandGenerator generates for both tools`() {
        val files = CommandGenerator.generate(buildDir, listOf("claude", "github-copilot"))
        assertEquals(34, files.size) // 17 commands * 2 tools
    }

    @Test
    fun `CommandGenerator with no tools generates nothing`() {
        val files = CommandGenerator.generate(buildDir, emptyList())
        assertEquals(0, files.size)
    }

    @Test
    fun `CommandGenerator with invalid tool generates nothing`() {
        val files = CommandGenerator.generate(buildDir, listOf("emacs"))
        assertEquals(0, files.size)
    }

    @Test
    fun `CommandGenerator files have non-empty content`() {
        val files = CommandGenerator.generate(buildDir, listOf("github-copilot"))
        files.forEach {
            assertTrue(it.file.readText().length > 50, "File ${it.relativePath} is too short")
        }
    }

    @Test
    fun `both generators combined produce correct total for 2-tool setup`() {
        val skills = SkillGenerator.generate(buildDir, listOf("claude", "github-copilot"))
        val commands = CommandGenerator.generate(buildDir, listOf("claude", "github-copilot"))
        assertEquals(34, skills.size)   // 17 * 2
        assertEquals(34, commands.size) // 17 * 2
        assertEquals(68, skills.size + commands.size)
    }
}
