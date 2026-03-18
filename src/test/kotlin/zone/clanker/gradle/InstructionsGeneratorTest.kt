package zone.clanker.gradle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.generators.InstructionsGenerator
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstructionsGeneratorTest {

    @TempDir
    lateinit var buildDir: File

    @Test
    fun `generates instruction file for claude`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("claude"))
        assertEquals(1, files.size)
        assertEquals(".claude/CLAUDE.md", files[0].relativePath)
        assertTrue(files[0].file.readText().contains("OPSX"))
        assertTrue(files[0].file.readText().contains("Golden Rule"))
    }

    @Test
    fun `generates instruction file for github-copilot`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("github-copilot"))
        assertEquals(1, files.size)
        assertEquals(".github/instructions/opsx.instructions.md", files[0].relativePath)
    }

    @Test
    fun `generates instruction file for codex`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("codex"))
        assertEquals(1, files.size)
        assertEquals(".codex/AGENTS.md", files[0].relativePath)
    }

    @Test
    fun `generates instruction file for opencode`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("opencode"))
        assertEquals(1, files.size)
        assertEquals(".opencode/instructions.md", files[0].relativePath)
    }

    @Test
    fun `generates instruction file for crush`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("crush"))
        assertEquals(1, files.size)
        assertEquals(".crush/CRUSH.md", files[0].relativePath)
    }

    @Test
    fun `generates instruction files for multiple agents`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("claude", "github-copilot", "codex"))
        assertEquals(3, files.size)
    }

    @Test
    fun `instruction content includes task catalog`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("claude"))
        val content = files[0].file.readText()
        assertTrue(content.contains("opsx-context"))
        assertTrue(content.contains("opsx-tree"))
        assertTrue(content.contains("opsx-find"))
        assertTrue(content.contains("opsx-arch"))
        assertTrue(content.contains("opsx-rename"))
    }

    @Test
    fun `instruction content forbids scripts`() {
        val files = InstructionsGenerator.generate(buildDir, listOf("claude"))
        val content = files[0].file.readText()
        assertTrue(content.contains("NEVER create Python"))
        assertTrue(content.contains("Bash"))
    }
}
