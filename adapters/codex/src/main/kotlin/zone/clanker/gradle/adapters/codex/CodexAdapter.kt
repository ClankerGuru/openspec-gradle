package zone.clanker.gradle.adapters.codex

import zone.clanker.gradle.generators.*

object CodexAdapter : ToolAdapter {
    override val toolId = "codex"
    override fun getSkillFilePath(skillDirName: String) = ".agents/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = "AGENTS.md"
    override val appendInstructions: Boolean get() = true
}
