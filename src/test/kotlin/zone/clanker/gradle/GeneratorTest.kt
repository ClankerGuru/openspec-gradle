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
    fun `SkillGenerator generates 4 skills per tool for core profile`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude"), "core")
        assertEquals(4, files.size)
        files.forEach { assertTrue(it.file.exists()) }
    }

    @Test
    fun `SkillGenerator generates 7 skills per tool for expanded profile`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude"), "expanded")
        assertEquals(7, files.size)
    }

    @Test
    fun `SkillGenerator generates for multiple tools`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude", "github-copilot"), "core")
        assertEquals(8, files.size) // 4 skills * 2 tools
    }

    @Test
    fun `SkillGenerator with no tools generates nothing`() {
        val files = SkillGenerator.generate(buildDir, emptyList(), "core")
        assertEquals(0, files.size)
    }

    @Test
    fun `SkillGenerator with invalid tool generates nothing`() {
        val files = SkillGenerator.generate(buildDir, listOf("vim"), "core")
        assertEquals(0, files.size)
    }

    @Test
    fun `SkillGenerator files have non-empty content`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude"), "core")
        files.forEach {
            assertTrue(it.file.readText().length > 50, "File ${it.relativePath} is too short")
        }
    }

    // ── CommandGenerator ────────────────────────────────

    @Test
    fun `CommandGenerator generates 4 commands per tool for core profile`() {
        val files = CommandGenerator.generate(buildDir, listOf("claude"), "core")
        assertEquals(4, files.size)
    }

    @Test
    fun `CommandGenerator generates 7 commands per tool for expanded profile`() {
        val files = CommandGenerator.generate(buildDir, listOf("github-copilot"), "expanded")
        assertEquals(7, files.size)
    }

    @Test
    fun `CommandGenerator generates for both tools`() {
        val files = CommandGenerator.generate(buildDir, listOf("claude", "github-copilot"), "core")
        assertEquals(8, files.size) // 4 commands * 2 tools
    }

    @Test
    fun `CommandGenerator with no tools generates nothing`() {
        val files = CommandGenerator.generate(buildDir, emptyList(), "core")
        assertEquals(0, files.size)
    }

    @Test
    fun `CommandGenerator with invalid tool generates nothing`() {
        val files = CommandGenerator.generate(buildDir, listOf("emacs"), "core")
        assertEquals(0, files.size)
    }

    @Test
    fun `CommandGenerator files have non-empty content`() {
        val files = CommandGenerator.generate(buildDir, listOf("github-copilot"), "core")
        files.forEach {
            assertTrue(it.file.readText().length > 50, "File ${it.relativePath} is too short")
        }
    }

    @Test
    fun `both generators combined produce correct total for expanded 2-tool setup`() {
        val skills = SkillGenerator.generate(buildDir, listOf("claude", "github-copilot"), "expanded")
        val commands = CommandGenerator.generate(buildDir, listOf("claude", "github-copilot"), "expanded")
        assertEquals(14, skills.size)   // 7 * 2
        assertEquals(14, commands.size) // 7 * 2
        assertEquals(28, skills.size + commands.size)
    }
}
