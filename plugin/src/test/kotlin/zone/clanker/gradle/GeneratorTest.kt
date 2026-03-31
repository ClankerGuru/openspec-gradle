package zone.clanker.gradle

import org.junit.jupiter.api.BeforeAll
import zone.clanker.gradle.adapters.claude.ClaudeAdapter
import zone.clanker.gradle.adapters.copilot.CopilotAdapter
import zone.clanker.gradle.adapters.codex.CodexAdapter
import zone.clanker.gradle.adapters.opencode.OpenCodeAdapter
import zone.clanker.gradle.generators.SkillGenerator
import zone.clanker.gradle.generators.ToolAdapterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerAdapters() {
            ToolAdapterRegistry.register(ClaudeAdapter)
            ToolAdapterRegistry.register(CopilotAdapter)
            ToolAdapterRegistry.register(CodexAdapter)
            ToolAdapterRegistry.register(OpenCodeAdapter)
        }
    }

    @TempDir
    lateinit var buildDir: File

    // ── SkillGenerator ──────────────────────────────────

    @Test
    fun `SkillGenerator generates 18 skills per tool`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude"))
        assertEquals(18, files.size)
        files.forEach { assertTrue(it.file.exists()) }
    }

    @Test
    fun `SkillGenerator generates for multiple tools`() {
        val files = SkillGenerator.generate(buildDir, listOf("claude", "github-copilot"))
        assertEquals(36, files.size) // 18 skills * 2 tools
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

    @Test
    fun `SkillGenerator produces correct total for 2-tool setup`() {
        val skills = SkillGenerator.generate(buildDir, listOf("claude", "github-copilot"))
        assertEquals(36, skills.size) // 18 * 2
    }
}
