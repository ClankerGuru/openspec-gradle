package zone.clanker.gradle

import zone.clanker.gradle.templates.TemplateRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateRegistryTest {

    @Test
    fun `has 16 command templates`() {
        val commands = TemplateRegistry.getCommandTemplates()
        assertEquals(16, commands.size)
        val ids = commands.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("propose", "apply", "archive", "explore", "new", "sync", "verify", "find", "calls", "rename", "status", "move", "usages", "extract", "inline", "deps")))
    }

    @Test
    fun `has 16 skill templates`() {
        val skills = TemplateRegistry.getSkillTemplates()
        assertEquals(16, skills.size)
    }

    @Test
    fun `all command templates have non-empty body`() {
        for (cmd in TemplateRegistry.getCommandTemplates()) {
            assertTrue(cmd.body.length > 50, "Command '${cmd.id}' body is too short")
            assertTrue(cmd.name.isNotBlank(), "Command '${cmd.id}' name is blank")
            assertTrue(cmd.description.isNotBlank(), "Command '${cmd.id}' description is blank")
            assertTrue(cmd.category.isNotBlank(), "Command '${cmd.id}' category is blank")
            assertTrue(cmd.tags.isNotEmpty(), "Command '${cmd.id}' has no tags")
        }
    }

    @Test
    fun `all skill templates have non-empty instructions`() {
        for (skill in TemplateRegistry.getSkillTemplates()) {
            assertTrue(skill.instructions.length > 50, "Skill '${skill.dirName}' instructions too short")
            assertTrue(skill.description.isNotBlank(), "Skill '${skill.dirName}' description is blank")
            assertTrue(skill.dirName.isNotBlank(), "Skill dirName is blank")
        }
    }

    @Test
    fun `propose command contains key instructions`() {
        val propose = TemplateRegistry.getCommandTemplates().first { it.id == "propose" }
        assertTrue(propose.body.contains("proposal.md"))
        assertTrue(propose.body.contains("design.md"))
        assertTrue(propose.body.contains("tasks.md"))
    }

    @Test
    fun `apply command contains task tracking instructions`() {
        val apply = TemplateRegistry.getCommandTemplates().first { it.id == "apply" }
        assertTrue(apply.body.contains("opsx-status"))
        assertTrue(apply.body.contains("--set=done"))
    }

    @Test
    fun `archive command references archive directory`() {
        val archive = TemplateRegistry.getCommandTemplates().first { it.id == "archive" }
        assertTrue(archive.body.contains("archive"))
        assertTrue(archive.body.contains("YYYY-MM-DD"))
    }

    @Test
    fun `explore command warns against implementation`() {
        val explore = TemplateRegistry.getCommandTemplates().first { it.id == "explore" }
        assertTrue(explore.body.contains("NEVER write code"))
    }

    @Test
    fun `skill dirNames use opsx prefix`() {
        for (skill in TemplateRegistry.getSkillTemplates()) {
            assertTrue(skill.dirName.startsWith("opsx-"), "Skill '${skill.dirName}' should start with opsx-")
        }
    }
}
