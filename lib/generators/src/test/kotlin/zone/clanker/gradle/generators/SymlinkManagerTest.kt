package zone.clanker.gradle.generators

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class SymlinkManagerTest {

    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var clkxDir: File

    @Test
    fun `createSymlinks creates symlinks to clkx dir`() {
        // Set up the clkx skills directory
        File(clkxDir, "skills/claude").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Instructions")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude"),
            clkxDir = clkxDir,
        )

        // Verify .claude/skills is a symlink pointing to clkx skills/claude
        val skillsLink = projectDir.toPath().resolve(".claude/skills")
        assertTrue(Files.isSymbolicLink(skillsLink), ".claude/skills should be a symlink")
        assertEquals(
            clkxDir.toPath().resolve("skills/claude"),
            Files.readSymbolicLink(skillsLink),
            "Symlink should point to ~/.clkx/skills/claude",
        )

        // Verify .claude/CLAUDE.md is a symlink pointing to clkx instructions/CLAUDE.md
        val instrLink = projectDir.toPath().resolve(".claude/CLAUDE.md")
        assertTrue(Files.isSymbolicLink(instrLink), ".claude/CLAUDE.md should be a symlink")
        assertEquals(
            clkxDir.toPath().resolve("instructions/CLAUDE.md"),
            Files.readSymbolicLink(instrLink),
            "Symlink should point to ~/.clkx/instructions/CLAUDE.md",
        )

        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/skills"])
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/CLAUDE.md"])
    }

    @Test
    fun `createSymlinks skips existing real directories`() {
        // Create a real .github/skills/ directory (team-committed)
        val realSkillsDir = File(projectDir, ".github/skills")
        realSkillsDir.mkdirs()
        File(realSkillsDir, "team-skill.md").writeText("Team skill content")

        // Also create the real instructions file
        File(projectDir, ".github/copilot-instructions.md").writeText("Team instructions")

        // Set up clkx target
        File(clkxDir, "skills/copilot").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/copilot-instructions.md").writeText("# Generated")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("github-copilot"),
            clkxDir = clkxDir,
        )

        // Skills dir should not be overwritten — it's a real directory
        assertEquals(
            SymlinkManager.LinkResult.REAL_FILE, results[".github/skills"],
            "Should return REAL_FILE for existing real directory",
        )
        assertFalse(
            Files.isSymbolicLink(projectDir.toPath().resolve(".github/skills")),
            "Real directory should not be replaced with a symlink",
        )

        // The team file should still be intact
        assertTrue(
            File(realSkillsDir, "team-skill.md").exists(),
            "Team skill file should not be deleted",
        )

        // Instructions file is also a real file
        assertEquals(
            SymlinkManager.LinkResult.REAL_FILE, results[".github/copilot-instructions.md"],
            "Should return REAL_FILE for existing real file",
        )
    }

    @Test
    fun `createSymlinks updates stale symlink`() {
        // Create a symlink pointing to the wrong target
        val claudeDir = File(projectDir, ".claude")
        claudeDir.mkdirs()
        val wrongTarget = File(clkxDir, "skills/wrong-target")
        wrongTarget.mkdirs()
        Files.createSymbolicLink(
            projectDir.toPath().resolve(".claude/skills"),
            wrongTarget.toPath(),
        )

        // Set up the correct clkx target
        val correctTarget = File(clkxDir, "skills/claude")
        correctTarget.mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Instructions")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude"),
            clkxDir = clkxDir,
        )

        // Verify the symlink was updated
        val skillsLink = projectDir.toPath().resolve(".claude/skills")
        assertTrue(Files.isSymbolicLink(skillsLink), ".claude/skills should still be a symlink")
        assertEquals(
            clkxDir.toPath().resolve("skills/claude"),
            Files.readSymbolicLink(skillsLink),
            "Symlink should now point to the correct target",
        )

        // Stale symlink was replaced → result is CREATED
        assertEquals(
            SymlinkManager.LinkResult.CREATED, results[".claude/skills"],
            "Should return CREATED after replacing stale symlink",
        )
    }

    @Test
    fun `createSymlinks returns CREATED for new symlinks`() {
        // Set up clkx directory structure for multiple agents
        File(clkxDir, "skills/claude").mkdirs()
        File(clkxDir, "skills/copilot").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Claude")
        File(clkxDir, "instructions/copilot-instructions.md").writeText("# Copilot")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude", "github-copilot"),
            clkxDir = clkxDir,
        )

        // All should be CREATED since nothing exists yet
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/skills"])
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/CLAUDE.md"])
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".github/skills"])
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".github/copilot-instructions.md"])

        // Verify the count
        assertEquals(4, results.size, "Should have results for all 4 link specs")

        // Verify all are actual symlinks
        for ((path, result) in results) {
            assertEquals(
                SymlinkManager.LinkResult.CREATED, result,
                "Expected CREATED for $path",
            )
            assertTrue(
                Files.isSymbolicLink(projectDir.toPath().resolve(path)),
                "$path should be a symlink",
            )
        }
    }

    @Test
    fun `createSymlinks returns SKIPPED for existing correct symlinks`() {
        // Create correct symlinks first
        File(clkxDir, "skills/claude").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Claude")

        val firstResults = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude"),
            clkxDir = clkxDir,
        )
        assertEquals(SymlinkManager.LinkResult.CREATED, firstResults[".claude/skills"])

        // Run again — should skip
        val secondResults = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude"),
            clkxDir = clkxDir,
        )

        assertEquals(
            SymlinkManager.LinkResult.SKIPPED, secondResults[".claude/skills"],
            "Should return SKIPPED for already-correct symlink",
        )
        assertEquals(
            SymlinkManager.LinkResult.SKIPPED, secondResults[".claude/CLAUDE.md"],
            "Should return SKIPPED for already-correct symlink",
        )
    }

    @Test
    fun `createSymlinks ignores unknown agent ids`() {
        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("unknown-agent"),
            clkxDir = clkxDir,
        )

        assertTrue(results.isEmpty(), "Should return empty results for unknown agent")
    }
}
