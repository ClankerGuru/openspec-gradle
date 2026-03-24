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

    private val sampleSkill = SkillContent(
        dirName = "test-skill",
        description = "A test skill",
        instructions = "Follow these instructions."
    )

    // ── Claude Adapter ──────────────────────────────────

    @Test
    fun `Claude skill file path is correct`() {
        assertEquals(".claude/skills/my-skill/SKILL.md", ClaudeAdapter.getSkillFilePath("my-skill"))
    }

    @Test
    fun `Claude skill file has YAML frontmatter with all fields`() {
        val output = ClaudeAdapter.formatSkillFile(sampleSkill)
        assertTrue(output.startsWith("---\n"), "Should start with YAML frontmatter")
        assertTrue(output.contains("name: test-skill"))
        assertTrue(output.contains("description:"))
        assertTrue(output.contains("license: MIT"))
        assertTrue(output.contains("compatibility:") && output.contains("Requires Gradle build system."))
        assertTrue(output.contains("metadata:"))
        assertTrue(output.contains("author:"))
        assertTrue(output.contains("generatedBy:"))
        assertTrue(output.contains("Follow these instructions."))
    }

    // ── GitHub Copilot Adapter ──────────────────────────

    @Test
    fun `Copilot skill file path is correct`() {
        assertEquals(".github/skills/my-skill/SKILL.md", CopilotAdapter.getSkillFilePath("my-skill"))
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
    fun `CodexAdapter skill path uses codex skills dir`() {
        assertEquals(".agents/skills/test-skill/SKILL.md", CodexAdapter.getSkillFilePath("test-skill"))
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
    fun `OpenCodeAdapter skill path uses opencode skills dir`() {
        assertEquals(".opencode/skills/test-skill/SKILL.md", OpenCodeAdapter.getSkillFilePath("test-skill"))
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
    fun `skill with special characters in description escapes yaml`() {
        val skill = sampleSkill.copy(description = "Uses: colons & stuff")
        val output = ClaudeAdapter.formatSkillFile(skill)
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
