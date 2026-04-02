package zone.clanker.gradle.generators

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import zone.clanker.gradle.core.VersionInfo

class VersionMetadataTest {

    private fun sampleSkillContent() = SkillContent(
        dirName = "test-skill",
        description = "A test skill for version metadata verification",
        instructions = "Do the thing."
    )

    @Test
    fun `formatSkillForClaude contains openspec-gradle version comment`() {
        val output = formatSkillForClaude(sampleSkillContent())
        assertTrue(
            output.contains("<!-- openspec-gradle:"),
            "Expected HTML comment with openspec-gradle version tag, got:\n$output"
        )
    }

    @Test
    fun `formatSkillForClaude version comment contains VersionInfo PLUGIN_VERSION`() {
        val output = formatSkillForClaude(sampleSkillContent())
        val version = VersionInfo.PLUGIN_VERSION
        assertTrue(
            output.contains("<!-- openspec-gradle:$version -->"),
            "Expected version comment with '$version', got:\n$output"
        )
    }

    @Test
    fun `formatSkillWithFrontmatter contains only name and description`() {
        val output = formatSkillWithFrontmatter(sampleSkillContent())
        assertTrue(output.contains("name:"), "Expected name field in frontmatter")
        assertTrue(output.contains("description:"), "Expected description field in frontmatter")
        assertFalse(output.contains("license:"), "Should not contain license field")
        assertFalse(output.contains("compatibility:"), "Should not contain compatibility field")
        assertFalse(output.contains("metadata:"), "Should not contain metadata block")
        assertFalse(output.contains("generatedBy:"), "Should not contain generatedBy field")
    }

    @Test
    fun `SkillContent default version matches VersionInfo`() {
        val content = sampleSkillContent()
        assertEquals(VersionInfo.PLUGIN_VERSION, content.version)
    }

    @Test
    fun `version is not the fallback 0_0_0 when properties resource is present`() {
        // This test documents the expectation that a real build packages the
        // openspec-gradle.properties resource.  During unit tests the resource
        // may or may not be on the classpath, so we only assert the version
        // string is well-formed (not blank) — the "not 0.0.0" check runs but
        // is advisory when the resource is absent.
        val version = VersionInfo.PLUGIN_VERSION
        assertTrue(version.isNotBlank(), "PLUGIN_VERSION must not be blank")

        // When the properties file IS on the classpath the version must not be
        // the fallback sentinel.
        val propsAvailable = VersionInfo::class.java.classLoader
            .getResourceAsStream("openspec-gradle.properties") != null
        if (propsAvailable) {
            assertNotEquals("0.0.0", version, "PLUGIN_VERSION should not be the fallback when properties resource exists")
        }
    }

    @Test
    fun `formatSkillForClaude version is not 0_0_0 when properties resource is present`() {
        val output = formatSkillForClaude(sampleSkillContent())
        val propsAvailable = VersionInfo::class.java.classLoader
            .getResourceAsStream("openspec-gradle.properties") != null
        if (propsAvailable) {
            assertFalse(
                output.contains("<!-- openspec-gradle:0.0.0 -->"),
                "Generated skill should not contain fallback version 0.0.0"
            )
        }
    }

    @Test
    fun `formatSkillWithFrontmatter produces clean minimal output`() {
        val output = formatSkillWithFrontmatter(sampleSkillContent())
        // Should start with --- and contain only name + description before closing ---
        val lines = output.lines()
        assertEquals("---", lines[0], "Should start with frontmatter delimiter")
        assertTrue(lines[1].startsWith("name:"), "Second line should be name")
        assertTrue(lines[2].startsWith("description:"), "Third line should be description")
        assertEquals("---", lines[3], "Fourth line should close frontmatter")
    }
}
