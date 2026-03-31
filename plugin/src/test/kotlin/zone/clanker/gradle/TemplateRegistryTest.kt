package zone.clanker.gradle

import zone.clanker.gradle.generators.TemplateRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateRegistryTest {

    @Test
    fun `has 18 skill templates`() {
        val skills = TemplateRegistry.getSkillTemplates()
        assertEquals(18, skills.size)
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
    fun `propose skill contains key instructions`() {
        val propose = TemplateRegistry.getSkillTemplates().first { it.dirName == "opsx-propose" }
        assertTrue(propose.instructions.contains("proposal.md"))
        assertTrue(propose.instructions.contains("design.md"))
        assertTrue(propose.instructions.contains("tasks.md"))
    }

    @Test
    fun `apply skill contains task tracking instructions`() {
        val apply = TemplateRegistry.getSkillTemplates().first { it.dirName == "opsx-apply" }
        assertTrue(apply.instructions.contains("opsx-status"))
        assertTrue(apply.instructions.contains("--set=done"))
    }

    @Test
    fun `archive skill references archive directory`() {
        val archive = TemplateRegistry.getSkillTemplates().first { it.dirName == "opsx-archive" }
        assertTrue(archive.instructions.contains("archive"))
        assertTrue(archive.instructions.contains("YYYY-MM-DD"))
    }

    @Test
    fun `explore skill warns against implementation`() {
        val explore = TemplateRegistry.getSkillTemplates().first { it.dirName == "opsx-explore" }
        assertTrue(explore.instructions.contains("NEVER write code"))
    }

    @Test
    fun `skill dirNames use opsx prefix`() {
        for (skill in TemplateRegistry.getSkillTemplates()) {
            assertTrue(skill.dirName.startsWith("opsx"), "Skill '${skill.dirName}' should start with opsx")
        }
    }
}
