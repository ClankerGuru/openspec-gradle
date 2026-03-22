package zone.clanker.gradle.adapters.claude

import zone.clanker.gradle.generators.*

object ClaudeAdapter : ToolAdapter {
    override val toolId = "claude"
    override fun getCommandFilePath(commandId: String) = ".claude/commands/opsx/$commandId.md"
    override fun formatCommandFile(content: CommandContent) = formatYamlCommandWithFrontmatter(content)
    override fun getSkillFilePath(skillDirName: String) = ".claude/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".claude/CLAUDE.md"
    override val appendInstructions: Boolean get() = true
}
