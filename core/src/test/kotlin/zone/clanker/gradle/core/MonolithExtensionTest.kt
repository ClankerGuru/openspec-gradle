package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MonolithExtensionTest {

    @Test
    fun `toCamelCase converts kebab-case`() {
        assertEquals("myLib", MonolithExtension.toCamelCase("my-lib"))
        assertEquals("sharedUtils", MonolithExtension.toCamelCase("shared-utils"))
        assertEquals("openspecGradle", MonolithExtension.toCamelCase("openspec-gradle"))
    }

    @Test
    fun `toCamelCase handles single word`() {
        assertEquals("core", MonolithExtension.toCamelCase("core"))
    }

    @Test
    fun `toCamelCase handles multiple dashes`() {
        assertEquals("myBigFatLib", MonolithExtension.toCamelCase("my-big-fat-lib"))
    }

    @Test
    fun `MonolithRepo enable toggles state`() {
        val repo = MonolithRepo("org/repo", "cat", emptyList(), defaultEnabled = true)
        assertTrue(repo.enabled)
        repo.enable(false)
        assertFalse(repo.enabled)
        repo.enable(true)
        assertTrue(repo.enabled)
    }

    @Test
    fun `DSL override wins over JSON default`() {
        val repo = MonolithRepo("org/lib", "cat", emptyList(), defaultEnabled = true)
        repo.enable(false)
        assertFalse(repo.enabled)
    }

    @Test
    fun `enabledEntries filters correctly`() {
        val ext = MonolithExtension()
        ext.register("libA", MonolithRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true))
        ext.register("libB", MonolithRepo("org/lib-b", "cat", emptyList(), defaultEnabled = false))
        ext.register("libC", MonolithRepo("org/lib-c", "cat", emptyList(), defaultEnabled = true))

        val enabled = ext.enabledEntries()
        assertEquals(2, enabled.size)
        assertTrue(enabled.any { it.repoName == "org/lib-a" })
        assertTrue(enabled.any { it.repoName == "org/lib-c" })
    }

    @Test
    fun `enabledEntries reflects DSL changes`() {
        val ext = MonolithExtension()
        ext.register("libA", MonolithRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true))
        ext.register("libB", MonolithRepo("org/lib-b", "cat", emptyList(), defaultEnabled = false))

        ext.repos["libA"]!!.enable(false)
        ext.repos["libB"]!!.enable(true)

        val enabled = ext.enabledEntries()
        assertEquals(1, enabled.size)
        assertEquals("org/lib-b", enabled[0].repoName)
    }
}
