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
    fun `formatSkillWithFrontmatter contains generatedBy with version`() {
        val output = formatSkillWithFrontmatter(sampleSkillContent())
        val version = VersionInfo.PLUGIN_VERSION
        // escapeYaml quotes values containing colons, so match the escaped form
        val expected = "generatedBy: ${escapeYaml("openspec-gradle:$version")}"
        assertTrue(
            output.contains(expected),
            "Expected '$expected' in frontmatter, got:\n$output"
        )
    }

    @Test
    fun `formatSkillWithFrontmatter generatedBy contains VersionInfo PLUGIN_VERSION`() {
        val output = formatSkillWithFrontmatter(sampleSkillContent())
        val version = VersionInfo.PLUGIN_VERSION
        // The generatedBy value is YAML-escaped, so verify the version string
        // appears somewhere in the generatedBy line regardless of quoting
        val generatedByLine = output.lines().firstOrNull { it.trim().startsWith("generatedBy:") }
        assertNotNull(generatedByLine, "Expected a generatedBy line in frontmatter, got:\n$output")
        assertTrue(
            generatedByLine!!.contains(version),
            "Expected version '$version' in generatedBy line '$generatedByLine'"
        )
    }

    @Test
    fun `SkillContent default metadata includes version from VersionInfo`() {
        val content = sampleSkillContent()
        assertEquals(VersionInfo.PLUGIN_VERSION, content.metadata["version"])
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
    fun `formatSkillWithFrontmatter version is not 0_0_0 when properties resource is present`() {
        val output = formatSkillWithFrontmatter(sampleSkillContent())
        val propsAvailable = VersionInfo::class.java.classLoader
            .getResourceAsStream("openspec-gradle.properties") != null
        if (propsAvailable) {
            val generatedByLine = output.lines().firstOrNull { it.trim().startsWith("generatedBy:") }
            assertNotNull(generatedByLine, "Expected a generatedBy line in frontmatter")
            assertFalse(
                generatedByLine!!.contains("0.0.0"),
                "Generated skill frontmatter should not contain fallback version 0.0.0, got: $generatedByLine"
            )
        }
    }
}
