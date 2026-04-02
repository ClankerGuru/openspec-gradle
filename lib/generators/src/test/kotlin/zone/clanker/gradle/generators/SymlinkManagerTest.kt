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
    fun `createSymlinks creates per-skill symlinks`() {
        // Set up clkx with 2 skill dirs
        File(clkxDir, "skills/claude/srcx-find").mkdirs()
        File(clkxDir, "skills/claude/srcx-find/SKILL.md").writeText("find skill")
        File(clkxDir, "skills/claude/opsx-propose").mkdirs()
        File(clkxDir, "skills/claude/opsx-propose/SKILL.md").writeText("propose skill")
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Instructions")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude"),
            clkxDir = clkxDir,
        )

        // .claude/skills should be a REAL directory (not a symlink)
        val skillsDir = File(projectDir, ".claude/skills")
        assertTrue(skillsDir.isDirectory, ".claude/skills should be a directory")
        assertFalse(Files.isSymbolicLink(skillsDir.toPath()), ".claude/skills should NOT be a symlink")

        // Each skill inside should be a symlink
        val findLink = skillsDir.toPath().resolve("srcx-find")
        assertTrue(Files.isSymbolicLink(findLink), "srcx-find should be a symlink")

        val proposeLink = skillsDir.toPath().resolve("opsx-propose")
        assertTrue(Files.isSymbolicLink(proposeLink), "opsx-propose should be a symlink")

        // Instructions file should be a symlink
        val instrLink = projectDir.toPath().resolve(".claude/CLAUDE.md")
        assertTrue(Files.isSymbolicLink(instrLink), ".claude/CLAUDE.md should be a symlink")

        // Results should include per-skill entries
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/skills/srcx-find"])
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/skills/opsx-propose"])
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/CLAUDE.md"])
    }

    @Test
    fun `project-specific skills coexist with symlinked skills`() {
        // Set up clkx
        File(clkxDir, "skills/claude/srcx-find").mkdirs()
        File(clkxDir, "skills/claude/srcx-find/SKILL.md").writeText("find skill")
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Instructions")

        // Create a project-specific skill FIRST
        val customSkill = File(projectDir, ".claude/skills/my-custom/SKILL.md")
        customSkill.parentFile.mkdirs()
        customSkill.writeText("my custom skill")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("claude"),
            clkxDir = clkxDir,
        )

        // Our symlink should be created
        assertTrue(Files.isSymbolicLink(File(projectDir, ".claude/skills/srcx-find").toPath()))

        // Project-specific skill should still exist as a REAL directory
        assertTrue(customSkill.exists(), "Custom skill should survive")
        assertFalse(Files.isSymbolicLink(File(projectDir, ".claude/skills/my-custom").toPath()),
            "Custom skill should NOT be a symlink")
        assertEquals("my custom skill", customSkill.readText())
    }

    @Test
    fun `createSymlinks skips existing real directories`() {
        // Team has a real copilot-instructions.md
        File(projectDir, ".github").mkdirs()
        File(projectDir, ".github/copilot-instructions.md").writeText("Team instructions")

        File(clkxDir, "skills/copilot/srcx-find").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/copilot-instructions.md").writeText("Generated")

        val results = SymlinkManager.createSymlinks(
            projectDir = projectDir,
            agents = listOf("github-copilot"),
            clkxDir = clkxDir,
        )

        // Instructions file is real — not overwritten
        assertEquals(SymlinkManager.LinkResult.REAL_FILE, results[".github/copilot-instructions.md"])
        assertEquals("Team instructions", File(projectDir, ".github/copilot-instructions.md").readText())

        // But per-skill symlinks should still be created
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".github/skills/srcx-find"])
    }

    @Test
    fun `createSymlinks returns SKIPPED for existing correct symlinks`() {
        File(clkxDir, "skills/claude/srcx-find").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Claude")

        // First run
        SymlinkManager.createSymlinks(projectDir = projectDir, agents = listOf("claude"), clkxDir = clkxDir)

        // Second run — should skip
        val results = SymlinkManager.createSymlinks(projectDir = projectDir, agents = listOf("claude"), clkxDir = clkxDir)

        assertEquals(SymlinkManager.LinkResult.SKIPPED, results[".claude/skills/srcx-find"])
        assertEquals(SymlinkManager.LinkResult.SKIPPED, results[".claude/CLAUDE.md"])
    }

    @Test
    fun `removeSymlinks removes our symlinks but keeps project skills`() {
        // Create per-skill symlinks
        File(clkxDir, "skills/claude/srcx-find").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Claude")
        SymlinkManager.createSymlinks(projectDir = projectDir, agents = listOf("claude"), clkxDir = clkxDir)

        // Add a project-specific skill
        val customSkill = File(projectDir, ".claude/skills/my-custom/SKILL.md")
        customSkill.parentFile.mkdirs()
        customSkill.writeText("custom")

        // Remove our symlinks
        val removed = SymlinkManager.removeSymlinks(projectDir = projectDir, agents = listOf("claude"), clkxDir = clkxDir)

        assertTrue(removed >= 2, "Should remove at least srcx-find symlink + CLAUDE.md symlink")

        // Our symlinks gone
        assertFalse(File(projectDir, ".claude/skills/srcx-find").exists(), "Our symlink should be gone")
        assertFalse(File(projectDir, ".claude/CLAUDE.md").exists(), "Instruction symlink should be gone")

        // Project skill survives
        assertTrue(customSkill.exists(), "Project skill should survive cleanup")
    }

    @Test
    fun `createSymlinks handles old-style directory symlink`() {
        // Simulate old-style: .claude/skills is a symlink to the whole dir
        val claudeDir = File(projectDir, ".claude")
        claudeDir.mkdirs()
        val oldTarget = File(clkxDir, "skills/claude")
        oldTarget.mkdirs()
        Files.createSymbolicLink(claudeDir.toPath().resolve("skills"), oldTarget.toPath())

        // Set up skills in clkx
        File(clkxDir, "skills/claude/srcx-find").mkdirs()
        File(clkxDir, "instructions").mkdirs()
        File(clkxDir, "instructions/CLAUDE.md").writeText("# Claude")

        // Should remove old symlink and create per-skill ones
        val results = SymlinkManager.createSymlinks(projectDir = projectDir, agents = listOf("claude"), clkxDir = clkxDir)

        // .claude/skills should now be a real directory
        assertFalse(Files.isSymbolicLink(File(projectDir, ".claude/skills").toPath()))
        assertTrue(File(projectDir, ".claude/skills").isDirectory)

        // Per-skill symlink created
        assertEquals(SymlinkManager.LinkResult.CREATED, results[".claude/skills/srcx-find"])
    }

    @Test
    fun `createSymlinks ignores unknown agent ids`() {
        val results = SymlinkManager.createSymlinks(projectDir = projectDir, agents = listOf("unknown"), clkxDir = clkxDir)
        assertTrue(results.isEmpty())
    }
}
