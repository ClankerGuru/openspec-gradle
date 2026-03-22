package zone.clanker.gradle

import zone.clanker.gradle.generators.*
import zone.clanker.gradle.adapters.claude.ClaudeAdapter
import zone.clanker.gradle.adapters.copilot.CopilotAdapter
import zone.clanker.gradle.adapters.codex.CodexAdapter
import zone.clanker.gradle.adapters.opencode.OpenCodeAdapter
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ToolAdapterTest {

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

    private val sampleCommand = CommandContent(
        id = "test-cmd",
        name = "Test Command",
        description = "A test command",
        category = "Testing",
        tags = listOf("test", "unit"),
        body = "Do the thing."
    )

    private val sampleSkill = SkillContent(
        dirName = "test-skill",
        description = "A test skill",
        instructions = "Follow these instructions."
    )

    // ── Claude Adapter ──────────────────────────────────

    @Test
    fun `Claude command file path is correct`() {
        assertEquals(".claude/commands/opsx/test-cmd.md", ClaudeAdapter.getCommandFilePath("test-cmd"))
    }

    @Test
    fun `Claude skill file path is correct`() {
        assertEquals(".claude/skills/my-skill/SKILL.md", ClaudeAdapter.getSkillFilePath("my-skill"))
    }

    @Test
    fun `Claude command frontmatter has name, description, category, tags`() {
        val output = ClaudeAdapter.formatCommandFile(sampleCommand)
        assertTrue(output.startsWith("---\n"))
        assertTrue(output.contains("name: \"Test Command\""))
        assertTrue(output.contains("description: \"A test command\""))
        assertTrue(output.contains("category: Testing"))
        assertTrue(output.contains("tags: [test, unit]"))
        assertTrue(output.contains("Do the thing."))
    }

    @Test
    fun `Claude skill file has YAML frontmatter with all fields`() {
        val output = ClaudeAdapter.formatSkillFile(sampleSkill)
        assertTrue(output.startsWith("---\n"), "Should start with YAML frontmatter")
        assertTrue(output.contains("name: test-skill"))
        assertTrue(output.contains("description:"))
        assertTrue(output.contains("license: MIT"))
        assertTrue(output.contains("compatibility: Requires Gradle build system."))
        assertTrue(output.contains("metadata:"))
        assertTrue(output.contains("author:"))
        assertTrue(output.contains("generatedBy:"))
        assertTrue(output.contains("Follow these instructions."))
    }

    // ── GitHub Copilot Adapter ──────────────────────────

    @Test
    fun `Copilot command file path is correct`() {
        assertEquals(".github/prompts/opsx-test-cmd.prompt.md", CopilotAdapter.getCommandFilePath("test-cmd"))
    }

    @Test
    fun `Copilot skill file path is correct`() {
        assertEquals(".github/skills/my-skill/SKILL.md", CopilotAdapter.getSkillFilePath("my-skill"))
    }

    @Test
    fun `Copilot command frontmatter only has description`() {
        val output = CopilotAdapter.formatCommandFile(sampleCommand)
        assertTrue(output.startsWith("---\n"))
        assertTrue(output.contains("description:"), "Should have description in frontmatter")
        assertTrue(output.contains("test command"), "Description should contain the text")
        assertTrue(!output.contains("category:"))
        assertTrue(!output.contains("tags:"))
    }

    // ── Registry ────────────────────────────────────────

    @Test
    fun `ToolAdapterRegistry returns correct adapters`() {
        assertEquals(ClaudeAdapter, ToolAdapterRegistry.get("claude"))
        assertEquals(CopilotAdapter, ToolAdapterRegistry.get("github-copilot"))
    }

    @Test
    fun `ToolAdapterRegistry returns null for unknown tool`() {
        assertNull(ToolAdapterRegistry.get("vim"))
        assertNull(ToolAdapterRegistry.get("cursor"))
    }

    @Test
    fun `ToolAdapterRegistry supportedTools lists all tools`() {
        assertEquals(setOf("claude", "github-copilot", "codex", "opencode"), ToolAdapterRegistry.supportedTools())
    }

    @Test
    fun `ToolAdapterRegistry all includes expected adapters`() {
        val ids = ToolAdapterRegistry.all().map { it.toolId }.toSet()
        assertTrue(ids.containsAll(setOf("claude", "github-copilot", "codex", "opencode")))
    }

    // ── Agent parsing ────────────────────────────────────

    @Test
    fun `parseAgents maps github to github-copilot`() {
        assertEquals(listOf("github-copilot"), OpenSpecSettingsPlugin.parseAgents("github"))
    }

    @Test
    fun `parseAgents maps opencode to opencode`() {
        assertEquals(listOf("opencode"), OpenSpecSettingsPlugin.parseAgents("opencode"))
    }

    @Test
    fun `parseAgents handles all three agents`() {
        assertEquals(listOf("github-copilot", "claude", "opencode"), OpenSpecSettingsPlugin.parseAgents("github,claude,opencode"))
    }

    @Test
    fun `parseAgents maps claude to claude`() {
        assertEquals(listOf("claude"), OpenSpecSettingsPlugin.parseAgents("claude"))
    }

    @Test
    fun `parseAgents handles comma-separated values`() {
        assertEquals(listOf("github-copilot", "claude"), OpenSpecSettingsPlugin.parseAgents("github,claude"))
    }

    @Test
    fun `parseAgents returns empty for none`() {
        assertEquals(emptyList(), OpenSpecSettingsPlugin.parseAgents("none"))
    }

    @Test
    fun `parseAgents returns empty for blank`() {
        assertEquals(emptyList(), OpenSpecSettingsPlugin.parseAgents(""))
        assertEquals(emptyList(), OpenSpecSettingsPlugin.parseAgents("  "))
    }

    @Test
    fun `parseAgents ignores unknown values`() {
        assertEquals(listOf("github-copilot"), OpenSpecSettingsPlugin.parseAgents("github,cursor,vim"))
    }

    @Test
    fun `parseAgents is case-insensitive`() {
        assertEquals(listOf("github-copilot", "claude"), OpenSpecSettingsPlugin.parseAgents("GitHub,Claude"))
    }

    // ── Codex adapter ────────────────────────────────────

    @Test
    fun `CodexAdapter command path uses codex skills dir`() {
        // Codex unifies commands as skills
        assertEquals(".codex/skills/opsx-propose/SKILL.md", CodexAdapter.getCommandFilePath("propose"))
    }

    @Test
    fun `CodexAdapter skill path uses codex skills dir`() {
        assertEquals(".codex/skills/test-skill/SKILL.md", CodexAdapter.getSkillFilePath("test-skill"))
    }

    @Test
    fun `CodexAdapter command format has YAML frontmatter`() {
        val output = CodexAdapter.formatCommandFile(sampleCommand)
        assertTrue(output.startsWith("---\n"))
        assertTrue(output.contains("name: opsx-test-cmd"))
        assertTrue(output.contains("description:"))
    }

    @Test
    fun `ToolAdapterRegistry returns CodexAdapter for codex`() {
        assertEquals(CodexAdapter, ToolAdapterRegistry.get("codex"))
    }

    @Test
    fun `parseAgents maps codex to codex`() {
        assertEquals(listOf("codex"), OpenSpecSettingsPlugin.parseAgents("codex"))
    }

    // ── OpenCode adapter ─────────────────────────────────

    @Test
    fun `OpenCodeAdapter command path uses opencode commands dir`() {
        assertEquals(".opencode/commands/opsx-propose.md", OpenCodeAdapter.getCommandFilePath("propose"))
    }

    @Test
    fun `OpenCodeAdapter skill path uses opencode skills dir`() {
        assertEquals(".opencode/skills/test-skill/SKILL.md", OpenCodeAdapter.getSkillFilePath("test-skill"))
    }

    @Test
    fun `OpenCodeAdapter command format has description comment`() {
        val output = OpenCodeAdapter.formatCommandFile(sampleCommand)
        assertTrue(output.contains("<!-- A test command -->"))
        assertTrue(output.contains("Do the thing."))
    }

    @Test
    fun `ToolAdapterRegistry returns OpenCodeAdapter for opencode`() {
        assertEquals(OpenCodeAdapter, ToolAdapterRegistry.get("opencode"))
    }

    @Test
    fun `parseAgents ignores unsupported agents like crush`() {
        assertEquals(emptyList<String>(), OpenSpecSettingsPlugin.parseAgents("crush"))
    }

    // ── YAML escaping ───────────────────────────────────

    @Test
    fun `command with special characters in description escapes yaml`() {
        val cmd = sampleCommand.copy(description = "Uses: colons & stuff")
        val output = ClaudeAdapter.formatCommandFile(cmd)
        assertTrue(output.contains("description: \"Uses: colons & stuff\""))
    }

    @Test
    fun `skill file format is consistent across all adapters`() {
        for (adapter in ToolAdapterRegistry.all()) {
            val output = adapter.formatSkillFile(sampleSkill)
            assertTrue(output.startsWith("---\n"), "${adapter.toolId} skill should start with YAML frontmatter")
            assertTrue(output.contains("name: test-skill"), "${adapter.toolId} skill should have name")
            assertTrue(output.contains("description:"), "${adapter.toolId} skill should have description")
            assertTrue(output.contains("license: MIT"), "${adapter.toolId} skill should have license")
            assertTrue(output.contains("compatibility:"), "${adapter.toolId} skill should have compatibility")
            assertTrue(output.contains("metadata:"), "${adapter.toolId} skill should have metadata")
            assertTrue(output.contains("generatedBy:"), "${adapter.toolId} skill should have generatedBy")
            assertTrue(output.contains("Follow these instructions."), "${adapter.toolId} skill should have instructions body")
        }
    }
}
