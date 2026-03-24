package zone.clanker.gradle.adapters.claude

import zone.clanker.gradle.generators.*

object ClaudeAdapter : ToolAdapter {
    override val toolId = "claude"
    override fun getSkillFilePath(skillDirName: String) = ".claude/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".claude/CLAUDE.md"
    override val appendInstructions: Boolean get() = true
}
