package zone.clanker.gradle.generators

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Test adapters that mirror ClaudeAdapter/CopilotAdapter without pulling in
 * the adapter modules (generators has no dependency on them).
 */
private object TestClaudeAdapter : ToolAdapter {
    override val toolId = "claude"
    override fun getSkillFilePath(skillDirName: String) = ".claude/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillForClaude(content)
    override fun getInstructionsFilePath() = ".claude/CLAUDE.md"
    override val appendInstructions: Boolean get() = true
}

private object TestCopilotAdapter : ToolAdapter {
    override val toolId = "github-copilot"
    override val globalDirName = "copilot"
    override fun getSkillFilePath(skillDirName: String) = ".github/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".github/copilot-instructions.md"
    override val appendInstructions: Boolean get() = true
}

class ClkxWriterTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        ToolAdapterRegistry.register(TestClaudeAdapter)
        ToolAdapterRegistry.register(TestCopilotAdapter)
    }

    @Test
    fun `writeSkills writes core skills for each agent`() {
        val count = ClkxWriter.writeSkills(tools = listOf("claude"), targetDir = tempDir)

        val expectedSkillCount = TemplateRegistry.getSkillTemplates().size
        assertTrue(expectedSkillCount >= 18, "TemplateRegistry should have at least 18 skill templates")
        assertEquals(expectedSkillCount, count, "writeSkills should write one file per skill template")

        // Verify each skill directory was created under skills/claude/
        val claudeSkillsDir = File(tempDir, "skills/claude")
        assertTrue(claudeSkillsDir.exists(), "skills/claude/ directory should exist")

        val skillDirs = claudeSkillsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        assertEquals(expectedSkillCount, skillDirs.size,
            "Expected $expectedSkillCount skill directories, found: ${skillDirs.map { it.name }}")

        // Each directory should contain a SKILL.md
        for (dir in skillDirs) {
            val skillFile = File(dir, "SKILL.md")
            assertTrue(skillFile.exists(), "Expected SKILL.md in ${dir.name}/")
            assertTrue(skillFile.readText().isNotBlank(), "SKILL.md in ${dir.name}/ should not be blank")
        }
    }

    @Test
    fun `writeSkills wipes and regenerates`() {
        // First write
        ClkxWriter.writeSkills(tools = listOf("claude"), targetDir = tempDir)

        // Add a stale file that shouldn't survive regeneration
        val staleDir = File(tempDir, "skills/claude/stale-skill")
        staleDir.mkdirs()
        val staleFile = File(staleDir, "SKILL.md")
        staleFile.writeText("I should be deleted")
        assertTrue(staleFile.exists(), "Stale file should exist before regeneration")

        // Second write -- should wipe and regenerate
        ClkxWriter.writeSkills(tools = listOf("claude"), targetDir = tempDir)

        assertFalse(staleFile.exists(), "Stale file should be deleted after regeneration")
        assertFalse(staleDir.exists(), "Stale directory should be deleted after regeneration")

        // Real skills should still be present
        val claudeSkillsDir = File(tempDir, "skills/claude")
        val skillDirs = claudeSkillsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        assertEquals(TemplateRegistry.getSkillTemplates().size, skillDirs.size,
            "Should still have all skill directories after regeneration")
    }

    @Test
    fun `writeSkills uses correct format per agent`() {
        // Write for Claude
        ClkxWriter.writeSkills(tools = listOf("claude"), targetDir = tempDir)

        val claudeSkillFile = File(tempDir, "skills/claude/srcx-find/SKILL.md")
        assertTrue(claudeSkillFile.exists(), "Claude skill file should exist")
        val claudeContent = claudeSkillFile.readText()

        // Claude format: starts with --- frontmatter, contains "name:" but NOT "license:" or "compatibility:"
        assertTrue(claudeContent.startsWith("---"), "Claude skill should start with frontmatter delimiter")
        assertTrue(claudeContent.contains("name:"), "Claude skill should contain name field")
        // Claude format omits license and compatibility
        assertFalse(claudeContent.contains("license:"), "Claude skill should not contain license field")
        assertFalse(claudeContent.contains("compatibility:"), "Claude skill should not contain compatibility field")

        // Now wipe and write for Copilot
        ClkxWriter.writeSkills(tools = listOf("github-copilot"), targetDir = tempDir)

        val copilotSkillFile = File(tempDir, "skills/copilot/srcx-find/SKILL.md")
        assertTrue(copilotSkillFile.exists(), "Copilot skill file should exist")
        val copilotContent = copilotSkillFile.readText()

        // Copilot uses minimal YAML frontmatter (name + description only)
        assertTrue(copilotContent.startsWith("---"), "Copilot skill should start with frontmatter delimiter")
        assertTrue(copilotContent.contains("name:"), "Copilot skill should contain name field")
        assertTrue(copilotContent.contains("description:"), "Copilot skill should contain description field")
        assertFalse(copilotContent.contains("license:"), "Copilot skill should not contain license field")
        assertFalse(copilotContent.contains("compatibility:"), "Copilot skill should not contain compatibility field")
        assertFalse(copilotContent.contains("metadata:"), "Copilot skill should not contain metadata block")
    }

    @Test
    fun `writeSkills with empty tools list writes nothing`() {
        val count = ClkxWriter.writeSkills(tools = emptyList(), targetDir = tempDir)

        assertEquals(0, count, "Should write zero files for empty tools list")

        val skillsDir = File(tempDir, "skills")
        // skills/ dir is created but should be empty (no agent subdirs with content)
        if (skillsDir.exists()) {
            val children = skillsDir.listFiles() ?: emptyArray()
            assertEquals(0, children.size,
                "skills/ directory should have no agent subdirectories for empty tools list")
        }
    }

    @Test
    fun `writeAll writes both skills and instructions`() {
        val instructionsContent = "# OPSX Instructions\nThese are test instructions."

        val count = ClkxWriter.writeAll(
            tools = listOf("claude"),
            instructionsContent = instructionsContent,
            targetDir = tempDir,
        )

        // Skills should be present
        val skillsDir = File(tempDir, "skills")
        assertTrue(skillsDir.exists(), "skills/ directory should exist after writeAll")
        assertTrue(count > 0, "writeAll should return a positive skill count")

        // Instructions should be present
        val instrDir = File(tempDir, "instructions")
        assertTrue(instrDir.exists(), "instructions/ directory should exist after writeAll")

        val instrFile = File(instrDir, "CLAUDE.md")
        assertTrue(instrFile.exists(), "CLAUDE.md instructions file should exist")
        assertEquals(instructionsContent, instrFile.readText(),
            "Instructions content should match what was passed in")
    }
}
