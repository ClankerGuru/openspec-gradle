package zone.clanker.gradle.adapters.copilot

import zone.clanker.gradle.generators.*

object CopilotAdapter : ToolAdapter {
    override val toolId = "github-copilot"
    override fun getCommandFilePath(commandId: String) = ".github/prompts/opsx-$commandId.prompt.md"
    override fun formatCommandFile(content: CommandContent) = """
        |---
        |description: ${escapeYaml(content.description)}
        |---
        |
        |${content.body}
    """.trimMargin() + "\n"
    override fun getSkillFilePath(skillDirName: String) = ".github/skills/$skillDirName/SKILL.md"
    override fun formatSkillFile(content: SkillContent) = formatSkillWithFrontmatter(content)
    override fun getInstructionsFilePath() = ".github/copilot-instructions.md"
    override val appendInstructions: Boolean get() = true
}
