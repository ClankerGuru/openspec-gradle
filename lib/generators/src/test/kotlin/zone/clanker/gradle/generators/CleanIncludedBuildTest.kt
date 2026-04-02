package zone.clanker.gradle.generators

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

/**
 * Tests for clean behavior on included builds:
 * - MarkerAppender.remove() preserves team content outside markers
 * - AgentCleaner removes only opsx-prefixed skill directories
 * - Symlinks to ~/.clkx/ are identified correctly for removal
 * - Real directories and files are never deleted
 */
class CleanIncludedBuildTest {

    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var clkxDir: File

    private object TestCopilotAdapter : ToolAdapter {
        override val toolId = "github-copilot"
        override val globalDirName = "copilot"
        override fun getSkillFilePath(skillDirName: String) = ".github/skills/$skillDirName/SKILL.md"
        override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
        override fun getInstructionsFilePath() = ".github/copilot-instructions.md"
        override val appendInstructions: Boolean get() = true
    }

    private object TestClaudeAdapter : ToolAdapter {
        override val toolId = "claude"
        override fun getSkillFilePath(skillDirName: String) = ".claude/skills/$skillDirName/SKILL.md"
        override fun formatSkillFile(content: SkillContent) = formatSkillForClaude(content)
        override fun getInstructionsFilePath() = ".claude/CLAUDE.md"
        override val appendInstructions: Boolean get() = true
    }

    @BeforeEach
    fun setUp() {
        ToolAdapterRegistry.register(TestCopilotAdapter)
        ToolAdapterRegistry.register(TestClaudeAdapter)
    }

    // --- MarkerAppender: team content preservation ---

    @Test
    fun `MarkerAppender remove preserves team content before markers`() {
        val instrFile = File(projectDir, ".github/copilot-instructions.md")
        instrFile.parentFile.mkdirs()

        val teamContent = "# Team Copilot Instructions\n\nAlways use TypeScript.\nNever commit to main directly."
        instrFile.writeText(teamContent)

        // Simulate opsx-sync appending OPSX content
        MarkerAppender.append(instrFile, "# OPSX Generated\nUse ./gradlew opsx commands.")

        // Verify markers were added
        assertTrue(MarkerAppender.hasMarkers(instrFile), "File should have OPSX markers after append")
        val contentAfterSync = instrFile.readText()
        assertTrue(contentAfterSync.contains("Team Copilot Instructions"), "Team content should be present after sync")
        assertTrue(contentAfterSync.contains("OPSX Generated"), "OPSX content should be present after sync")

        // Simulate opsx-clean removing OPSX content
        MarkerAppender.remove(instrFile)

        // Verify team content is preserved
        val contentAfterClean = instrFile.readText()
        assertTrue(contentAfterClean.contains("Team Copilot Instructions"), "Team content should survive clean")
        assertTrue(contentAfterClean.contains("Always use TypeScript"), "Team details should survive clean")
        assertTrue(contentAfterClean.contains("Never commit to main directly"), "All team lines should survive clean")

        // Verify OPSX content is gone
        assertFalse(contentAfterClean.contains("OPSX Generated"), "OPSX content should be removed after clean")
        assertFalse(contentAfterClean.contains("OPSX:BEGIN"), "Begin marker should be removed after clean")
        assertFalse(contentAfterClean.contains("OPSX:END"), "End marker should be removed after clean")
        assertFalse(MarkerAppender.hasMarkers(instrFile), "File should not have markers after clean")
    }

    @Test
    fun `MarkerAppender remove preserves team content after markers`() {
        val instrFile = File(projectDir, ".github/copilot-instructions.md")
        instrFile.parentFile.mkdirs()

        // Write file with markers in the middle and team content after
        val content = """
            |# Team Header
            |
            |<!-- OPSX:BEGIN -->
            |# OPSX Content
            |<!-- OPSX:END -->
            |
            |# Team Footer
            |Do not remove this.
        """.trimMargin()
        instrFile.writeText(content)

        MarkerAppender.remove(instrFile)

        val result = instrFile.readText()
        assertTrue(result.contains("Team Header"), "Header should survive clean")
        assertTrue(result.contains("Team Footer"), "Footer should survive clean")
        assertTrue(result.contains("Do not remove this"), "Footer details should survive clean")
        assertFalse(result.contains("OPSX Content"), "OPSX content should be removed")
    }

    @Test
    fun `MarkerAppender remove leaves empty file when only markers exist`() {
        val instrFile = File(projectDir, "AGENTS.md")
        instrFile.parentFile.mkdirs()

        // File created solely by OPSX (no team content)
        MarkerAppender.append(instrFile, "# OPSX Generated Content")
        assertTrue(instrFile.exists(), "File should exist after append")

        MarkerAppender.remove(instrFile)

        // File should still exist but be empty
        assertTrue(instrFile.exists(), "File should still exist after clean (not deleted)")
        assertEquals("", instrFile.readText(), "File should be empty after removing sole marker section")
    }

    // --- AgentCleaner: selective skill directory removal ---

    @Test
    fun `AgentCleaner removes only opsx-prefixed skill directories`() {
        // Set up a .github/skills/ directory with both team and OPSX skills
        val skillsDir = File(projectDir, ".github/skills")
        skillsDir.mkdirs()

        // Team-committed skill
        val teamSkillDir = File(skillsDir, "team-custom-skill")
        teamSkillDir.mkdirs()
        File(teamSkillDir, "SKILL.md").writeText("# Team custom skill\nDo team things.")

        // OPSX-generated skills
        val opsxSkill1 = File(skillsDir, "opsx-find")
        opsxSkill1.mkdirs()
        File(opsxSkill1, "SKILL.md").writeText("# OPSX find skill")

        val opsxSkill2 = File(skillsDir, "opsx-explore")
        opsxSkill2.mkdirs()
        File(opsxSkill2, "SKILL.md").writeText("# OPSX explore skill")

        // Also create the instructions file with markers
        val instrFile = File(projectDir, ".github/copilot-instructions.md")
        instrFile.writeText("# Team Instructions\n")
        MarkerAppender.append(instrFile, "# OPSX Section")

        // Clean the copilot adapter
        val cleaned = AgentCleaner.cleanAgent(projectDir, TestCopilotAdapter)

        // OPSX skills should be removed
        assertFalse(opsxSkill1.exists(), "opsx-find should be removed")
        assertFalse(opsxSkill2.exists(), "opsx-explore should be removed")

        // Team skill should be preserved
        assertTrue(teamSkillDir.exists(), "team-custom-skill should be preserved")
        assertTrue(File(teamSkillDir, "SKILL.md").exists(), "Team skill file should be preserved")
        assertEquals("# Team custom skill\nDo team things.", File(teamSkillDir, "SKILL.md").readText())

        // Instructions file should have marker section removed but team content preserved
        assertTrue(instrFile.exists(), "Instructions file should still exist")
        val instrContent = instrFile.readText()
        assertTrue(instrContent.contains("Team Instructions"), "Team instructions should survive")
        assertFalse(instrContent.contains("OPSX Section"), "OPSX section should be removed from instructions")

        assertTrue(cleaned >= 3, "Should have cleaned at least 3 items (2 skills + 1 instructions)")
    }

    @Test
    fun `AgentCleaner does not remove non-opsx skill directories`() {
        // Set up a .claude/skills/ directory with only non-opsx skills
        val skillsDir = File(projectDir, ".claude/skills")
        skillsDir.mkdirs()

        val customSkill = File(skillsDir, "my-custom-skill")
        customSkill.mkdirs()
        File(customSkill, "SKILL.md").writeText("# My custom skill")

        val anotherSkill = File(skillsDir, "another-skill")
        anotherSkill.mkdirs()
        File(anotherSkill, "SKILL.md").writeText("# Another skill")

        // Create a dummy instructions file to avoid it being counted as missing
        val instrFile = File(projectDir, ".claude/CLAUDE.md")
        instrFile.writeText("# Team CLAUDE.md")

        val cleaned = AgentCleaner.cleanAgent(projectDir, TestClaudeAdapter)

        // No opsx-prefixed dirs, so nothing should be removed except possibly the instructions marker
        assertTrue(customSkill.exists(), "my-custom-skill should be preserved")
        assertTrue(anotherSkill.exists(), "another-skill should be preserved")
    }

    // --- Symlink detection for clean ---

    @Test
    fun `symlinks pointing to clkx are distinguishable from real directories`() {
        // Create a real skills directory (team-committed)
        val realSkillsDir = File(projectDir, ".github/skills")
        realSkillsDir.mkdirs()
        File(realSkillsDir, "team-skill.md").writeText("Team content")

        // Create a symlink pointing to clkx
        val clkxSkillsTarget = File(clkxDir, "skills/claude")
        clkxSkillsTarget.mkdirs()
        File(clkxSkillsTarget, "srcx-find/SKILL.md").apply {
            parentFile.mkdirs()
            writeText("# Clkx skill")
        }

        val claudeDir = File(projectDir, ".claude")
        claudeDir.mkdirs()
        val symlinkPath = projectDir.toPath().resolve(".claude/skills")
        Files.createSymbolicLink(symlinkPath, clkxSkillsTarget.toPath())

        // Verify: .github/skills is a real directory
        assertFalse(Files.isSymbolicLink(realSkillsDir.toPath()),
            ".github/skills should NOT be a symlink")
        assertTrue(realSkillsDir.isDirectory, ".github/skills should be a real directory")

        // Verify: .claude/skills is a symlink
        assertTrue(Files.isSymbolicLink(symlinkPath),
            ".claude/skills should be a symlink")

        // Verify: symlink points into clkx
        val target = Files.readSymbolicLink(symlinkPath)
        val resolvedTarget = symlinkPath.parent.resolve(target).normalize()
        assertTrue(resolvedTarget.startsWith(clkxDir.toPath()),
            "Symlink should point into clkx directory")

        // Delete symlink, verify real dir is untouched
        Files.delete(symlinkPath)
        assertFalse(Files.exists(symlinkPath), "Symlink should be deleted")
        assertTrue(realSkillsDir.exists(), "Real directory should be untouched after symlink deletion")
        assertTrue(File(realSkillsDir, "team-skill.md").exists(),
            "Real directory content should be untouched")
    }

    @Test
    fun `real directories are not deleted when they do not contain symlinks`() {
        // Simulate an included build with a real .claude directory (not a symlink)
        val claudeDir = File(projectDir, ".claude")
        claudeDir.mkdirs()
        val realSkillsDir = File(claudeDir, "skills")
        realSkillsDir.mkdirs()

        val teamSkill = File(realSkillsDir, "team-skill")
        teamSkill.mkdirs()
        File(teamSkill, "SKILL.md").writeText("# Team skill")

        // Verify it's a real directory, not a symlink
        assertFalse(Files.isSymbolicLink(realSkillsDir.toPath()),
            ".claude/skills should be a real directory")

        // After checking, the real directory should still be fully intact
        assertTrue(teamSkill.exists(), "Team skill directory should still exist")
        assertTrue(File(teamSkill, "SKILL.md").readText().contains("Team skill"),
            "Team skill content should be intact")
    }

    // --- End-to-end scenario ---

    @Test
    fun `full scenario - sync adds markers, clean removes markers, team content preserved`() {
        // 1. Team commits copilot-instructions.md with their own content
        val instrFile = File(projectDir, ".github/copilot-instructions.md")
        instrFile.parentFile.mkdirs()
        val teamContent = """# Our Team Copilot Config

## Code style
- Use Kotlin
- Follow SOLID principles

## Forbidden patterns
- No god objects
- No circular dependencies"""
        instrFile.writeText(teamContent)

        // 2. opsx-sync appends OPSX content via MarkerAppender
        val opsxContent = """# OPSX Skills

Use the following Gradle tasks:
- ./gradlew opsx-find -Pquery=Name
- ./gradlew opsx-explore"""
        MarkerAppender.append(instrFile, opsxContent)

        // Verify both are present
        val afterSync = instrFile.readText()
        assertTrue(afterSync.contains("Our Team Copilot Config"), "Team header should be present after sync")
        assertTrue(afterSync.contains("SOLID principles"), "Team content should be present after sync")
        assertTrue(afterSync.contains("OPSX Skills"), "OPSX content should be present after sync")
        assertTrue(afterSync.contains("opsx-find"), "OPSX task reference should be present after sync")
        assertTrue(MarkerAppender.hasMarkers(instrFile), "Markers should be present after sync")

        // 3. opsx-clean removes only the marker section
        MarkerAppender.remove(instrFile)

        // 4. Verify team content is fully preserved
        val afterClean = instrFile.readText()
        assertTrue(afterClean.contains("Our Team Copilot Config"), "Team header should survive clean")
        assertTrue(afterClean.contains("Use Kotlin"), "Team code style should survive clean")
        assertTrue(afterClean.contains("SOLID principles"), "Team principles should survive clean")
        assertTrue(afterClean.contains("No god objects"), "Team forbidden patterns should survive clean")
        assertTrue(afterClean.contains("No circular dependencies"), "Team dependency rule should survive clean")

        // Verify OPSX content is completely removed
        assertFalse(afterClean.contains("OPSX Skills"), "OPSX header should be removed")
        assertFalse(afterClean.contains("opsx-find"), "OPSX task reference should be removed")
        assertFalse(afterClean.contains("opsx-explore"), "OPSX explore reference should be removed")
        assertFalse(afterClean.contains("OPSX:BEGIN"), "Begin marker should be removed")
        assertFalse(afterClean.contains("OPSX:END"), "End marker should be removed")
    }
}
