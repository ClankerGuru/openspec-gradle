package zone.clanker.gradle.adapters.codex

import zone.clanker.gradle.generators.*

object CodexAdapter : ToolAdapter {
    override val toolId = "codex"
    override fun getCommandFilePath(commandId: String) = ".codex/skills/opsx-$commandId/SKILL.md"
    override fun formatCommandFile(content: CommandContent) =
        formatSkillWithFrontmatter(SkillContent(
            dirName = "opsx-${content.id}",
            description = content.description,
            instructions = content.body
        ))
    override fun getSkillFilePath(skillDirName: String) = ".codex/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = "AGENTS.md"
    override val appendInstructions: Boolean get() = true
}
