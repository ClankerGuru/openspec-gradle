package zone.clanker.gradle.adapters.copilot

import zone.clanker.gradle.generators.*

object CopilotAdapter : ToolAdapter {
    override val toolId = "github-copilot"
    override val globalDirName = "copilot"
    override fun getSkillFilePath(skillDirName: String) = ".github/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".github/copilot-instructions.md"
    override val appendInstructions: Boolean get() = true
}
