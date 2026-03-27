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
    fun `toCamelCase handles spaces`() {
        assertEquals("coreModelsLib", MonolithExtension.toCamelCase("Core Models Lib"))
        assertEquals("myCoolProject", MonolithExtension.toCamelCase("My Cool Project"))
    }

    @Test
    fun `toCamelCase handles underscores`() {
        assertEquals("fooBar", MonolithExtension.toCamelCase("foo_bar"))
        assertEquals("fooBarBaz", MonolithExtension.toCamelCase("foo_bar_baz"))
    }

    @Test
    fun `toCamelCase handles mixed separators`() {
        assertEquals("myFooBar", MonolithExtension.toCamelCase("my-foo_bar"))
        assertEquals("coreModelsLib", MonolithExtension.toCamelCase("core-models lib"))
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

    // --- sanitizedBuildName ---

    @Test
    fun `sanitizedBuildName passes through normal names`() {
        val repo = MonolithRepo("org/my-lib", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName lowercases`() {
        val repo = MonolithRepo("org/MyLib", "cat", emptyList(), defaultEnabled = true)
        assertEquals("mylib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName replaces spaces with hyphens`() {
        val repo = MonolithRepo("org/My Cool Project", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-cool-project", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName replaces underscores with hyphens`() {
        val repo = MonolithRepo("org/foo__bar", "cat", emptyList(), defaultEnabled = true)
        assertEquals("foo-bar", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName replaces special chars`() {
        val repo = MonolithRepo("org/bazLib (v2)", "cat", emptyList(), defaultEnabled = true)
        assertEquals("bazlib-v2", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName collapses consecutive hyphens`() {
        val repo = MonolithRepo("org/my---lib", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName trims leading and trailing hyphens`() {
        val repo = MonolithRepo("org/-my-lib-", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName throws on all-symbol input`() {
        val repo = MonolithRepo("org/---", "cat", emptyList(), defaultEnabled = true)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            repo.sanitizedBuildName
        }
        assertTrue(ex.message!!.contains("Invalid build name"))
        assertTrue(ex.message!!.contains("---"))
    }

    @Test
    fun `sanitizedBuildName throws on all-underscore input`() {
        val repo = MonolithRepo("org/___", "cat", emptyList(), defaultEnabled = true)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            repo.sanitizedBuildName
        }
        assertTrue(ex.message!!.contains("Invalid build name"))
    }

    @Test
    fun `sanitizedBuildName handles mixed special chars`() {
        val repo = MonolithRepo("org/My Lib (core)_v2", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib-core-v2", repo.sanitizedBuildName)
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

    // --- duplicate name detection (cbd-7) ---

    @Test
    fun `checkForDuplicateBuildNames passes with unique names`() {
        val ext = MonolithExtension()
        val repos = listOf(
            MonolithRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/lib-b", "cat", emptyList(), defaultEnabled = true)
        )
        // Should not throw
        ext.checkForDuplicateBuildNames(repos)
    }

    @Test
    fun `checkForDuplicateBuildNames throws on collision`() {
        val ext = MonolithExtension()
        // "My Lib" and "my_lib" both sanitize to "my-lib"
        val repos = listOf(
            MonolithRepo("org/My Lib", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/my_lib", "cat", emptyList(), defaultEnabled = true)
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            ext.checkForDuplicateBuildNames(repos)
        }
        assertTrue(ex.message!!.contains("Duplicate sanitized build names"))
        assertTrue(ex.message!!.contains("my-lib"))
        assertTrue(ex.message!!.contains("org/My Lib"))
        assertTrue(ex.message!!.contains("org/my_lib"))
    }

    @Test
    fun `checkForDuplicateBuildNames reports all collisions`() {
        val ext = MonolithExtension()
        val repos = listOf(
            MonolithRepo("org/My Lib", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/my_lib", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/Foo Bar", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/foo_bar", "cat", emptyList(), defaultEnabled = true)
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            ext.checkForDuplicateBuildNames(repos)
        }
        assertTrue(ex.message!!.contains("my-lib"))
        assertTrue(ex.message!!.contains("foo-bar"))
    }

    @Test
    fun `checkForDuplicateBuildNames ignores non-colliding names`() {
        val ext = MonolithExtension()
        val repos = listOf(
            MonolithRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/My Lib", "cat", emptyList(), defaultEnabled = true),
            MonolithRepo("org/my_lib", "cat", emptyList(), defaultEnabled = true)
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            ext.checkForDuplicateBuildNames(repos)
        }
        assertTrue(ex.message!!.contains("my-lib"))
        assertFalse(ex.message!!.contains("lib-a"))
    }

    // --- includeBuild() chaining (cbd-9) ---

    @Test
    fun `includeBuild adds repos to includedBuilds`() {
        val a = MonolithRepo("org/a", "cat", emptyList(), defaultEnabled = true)
        val b = MonolithRepo("org/b", "cat", emptyList(), defaultEnabled = true)
        val c = MonolithRepo("org/c", "cat", emptyList(), defaultEnabled = true)

        a.includeBuild(b, c)

        assertEquals(2, a.includedBuilds.size)
        assertTrue(a.includedBuilds.contains(b))
        assertTrue(a.includedBuilds.contains(c))
    }

    @Test
    fun `includeBuild returns this for chaining`() {
        val a = MonolithRepo("org/a", "cat", emptyList(), defaultEnabled = true)
        val b = MonolithRepo("org/b", "cat", emptyList(), defaultEnabled = true)

        val result = a.includeBuild(b)
        assertSame(a, result)
    }

    @Test
    fun `includeBuild can be called multiple times`() {
        val a = MonolithRepo("org/a", "cat", emptyList(), defaultEnabled = true)
        val b = MonolithRepo("org/b", "cat", emptyList(), defaultEnabled = true)
        val c = MonolithRepo("org/c", "cat", emptyList(), defaultEnabled = true)

        a.includeBuild(b).includeBuild(c)

        assertEquals(2, a.includedBuilds.size)
    }

    @Test
    fun `includedBuilds is empty by default`() {
        val a = MonolithRepo("org/a", "cat", emptyList(), defaultEnabled = true)
        assertTrue(a.includedBuilds.isEmpty())
    }

    @Test
    fun `includedBuilds returns defensive copy`() {
        val a = MonolithRepo("org/a", "cat", emptyList(), defaultEnabled = true)
        val b = MonolithRepo("org/b", "cat", emptyList(), defaultEnabled = true)
        a.includeBuild(b)

        val list1 = a.includedBuilds
        val list2 = a.includedBuilds
        assertEquals(list1, list2)
        assertNotSame(list1, list2)
    }
}
