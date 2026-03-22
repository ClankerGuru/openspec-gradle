package zone.clanker.gradle.adapters.opencode

import zone.clanker.gradle.generators.*

object OpenCodeAdapter : ToolAdapter {
    override val toolId = "opencode"
    override fun getCommandFilePath(commandId: String) = ".opencode/commands/opsx-$commandId.md"
    override fun formatCommandFile(content: CommandContent) = buildString {
        appendLine("<!-- ${content.description} -->")
        appendLine()
        append(content.body)
        appendLine()
    }
    override fun getSkillFilePath(skillDirName: String) = ".opencode/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = "AGENTS.md"
    override val appendInstructions: Boolean get() = true
}
