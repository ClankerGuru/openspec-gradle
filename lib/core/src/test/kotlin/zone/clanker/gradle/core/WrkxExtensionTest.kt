package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WrkxExtensionTest {

    @Test
    fun `toCamelCase converts kebab-case`() {
        assertEquals("myLib", WrkxExtension.toCamelCase("my-lib"))
        assertEquals("sharedUtils", WrkxExtension.toCamelCase("shared-utils"))
        assertEquals("openspecGradle", WrkxExtension.toCamelCase("openspec-gradle"))
    }

    @Test
    fun `toCamelCase handles single word`() {
        assertEquals("core", WrkxExtension.toCamelCase("core"))
    }

    @Test
    fun `toCamelCase handles multiple dashes`() {
        assertEquals("myBigFatLib", WrkxExtension.toCamelCase("my-big-fat-lib"))
    }

    @Test
    fun `toCamelCase handles spaces`() {
        assertEquals("coreModelsLib", WrkxExtension.toCamelCase("Core Models Lib"))
        assertEquals("myCoolProject", WrkxExtension.toCamelCase("My Cool Project"))
    }

    @Test
    fun `toCamelCase handles underscores`() {
        assertEquals("fooBar", WrkxExtension.toCamelCase("foo_bar"))
        assertEquals("fooBarBaz", WrkxExtension.toCamelCase("foo_bar_baz"))
    }

    @Test
    fun `toCamelCase handles mixed separators`() {
        assertEquals("myFooBar", WrkxExtension.toCamelCase("my-foo_bar"))
        assertEquals("coreModelsLib", WrkxExtension.toCamelCase("core-models lib"))
    }

    @Test
    fun `WrkxRepo enable toggles state`() {
        val repo = WrkxRepo("org/repo", "cat", emptyList(), defaultEnabled = true)
        assertTrue(repo.enabled)
        repo.enable(false)
        assertFalse(repo.enabled)
        repo.enable(true)
        assertTrue(repo.enabled)
    }

    @Test
    fun `DSL override wins over JSON default`() {
        val repo = WrkxRepo("org/lib", "cat", emptyList(), defaultEnabled = true)
        repo.enable(false)
        assertFalse(repo.enabled)
    }

    @Test
    fun `enabledEntries filters correctly`() {
        val ext = WrkxExtension()
        ext.register("libA", WrkxRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true))
        ext.register("libB", WrkxRepo("org/lib-b", "cat", emptyList(), defaultEnabled = false))
        ext.register("libC", WrkxRepo("org/lib-c", "cat", emptyList(), defaultEnabled = true))

        val enabled = ext.enabledEntries()
        assertEquals(2, enabled.size)
        assertTrue(enabled.any { it.repoName == "org/lib-a" })
        assertTrue(enabled.any { it.repoName == "org/lib-c" })
    }

    // --- sanitizedBuildName ---

    @Test
    fun `sanitizedBuildName passes through normal names`() {
        val repo = WrkxRepo("org/my-lib", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName lowercases`() {
        val repo = WrkxRepo("org/MyLib", "cat", emptyList(), defaultEnabled = true)
        assertEquals("mylib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName replaces spaces with hyphens`() {
        val repo = WrkxRepo("org/My Cool Project", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-cool-project", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName replaces underscores with hyphens`() {
        val repo = WrkxRepo("org/foo__bar", "cat", emptyList(), defaultEnabled = true)
        assertEquals("foo-bar", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName replaces special chars`() {
        val repo = WrkxRepo("org/bazLib (v2)", "cat", emptyList(), defaultEnabled = true)
        assertEquals("bazlib-v2", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName collapses consecutive hyphens`() {
        val repo = WrkxRepo("org/my---lib", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName trims leading and trailing hyphens`() {
        val repo = WrkxRepo("org/-my-lib-", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib", repo.sanitizedBuildName)
    }

    @Test
    fun `sanitizedBuildName throws on all-symbol input`() {
        val repo = WrkxRepo("org/---", "cat", emptyList(), defaultEnabled = true)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            repo.sanitizedBuildName
        }
        assertTrue(ex.message!!.contains("Invalid build name"))
        assertTrue(ex.message!!.contains("---"))
    }

    @Test
    fun `sanitizedBuildName throws on all-underscore input`() {
        val repo = WrkxRepo("org/___", "cat", emptyList(), defaultEnabled = true)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            repo.sanitizedBuildName
        }
        assertTrue(ex.message!!.contains("Invalid build name"))
    }

    @Test
    fun `sanitizedBuildName handles mixed special chars`() {
        val repo = WrkxRepo("org/My Lib (core)_v2", "cat", emptyList(), defaultEnabled = true)
        assertEquals("my-lib-core-v2", repo.sanitizedBuildName)
    }

    @Test
    fun `enabledEntries reflects DSL changes`() {
        val ext = WrkxExtension()
        ext.register("libA", WrkxRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true))
        ext.register("libB", WrkxRepo("org/lib-b", "cat", emptyList(), defaultEnabled = false))

        ext.repos["libA"]!!.enable(false)
        ext.repos["libB"]!!.enable(true)

        val enabled = ext.enabledEntries()
        assertEquals(1, enabled.size)
        assertEquals("org/lib-b", enabled[0].repoName)
    }

    // --- duplicate name detection (cbd-7) ---

    @Test
    fun `checkForDuplicateBuildNames passes with unique names`() {
        val ext = WrkxExtension()
        val repos = listOf(
            WrkxRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/lib-b", "cat", emptyList(), defaultEnabled = true)
        )
        // Should not throw
        ext.checkForDuplicateBuildNames(repos)
    }

    @Test
    fun `checkForDuplicateBuildNames throws on collision`() {
        val ext = WrkxExtension()
        // "My Lib" and "my_lib" both sanitize to "my-lib"
        val repos = listOf(
            WrkxRepo("org/My Lib", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/my_lib", "cat", emptyList(), defaultEnabled = true)
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
        val ext = WrkxExtension()
        val repos = listOf(
            WrkxRepo("org/My Lib", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/my_lib", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/Foo Bar", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/foo_bar", "cat", emptyList(), defaultEnabled = true)
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            ext.checkForDuplicateBuildNames(repos)
        }
        assertTrue(ex.message!!.contains("my-lib"))
        assertTrue(ex.message!!.contains("foo-bar"))
    }

    @Test
    fun `checkForDuplicateBuildNames ignores non-colliding names`() {
        val ext = WrkxExtension()
        val repos = listOf(
            WrkxRepo("org/lib-a", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/My Lib", "cat", emptyList(), defaultEnabled = true),
            WrkxRepo("org/my_lib", "cat", emptyList(), defaultEnabled = true)
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            ext.checkForDuplicateBuildNames(repos)
        }
        assertTrue(ex.message!!.contains("my-lib"))
        assertFalse(ex.message!!.contains("lib-a"))
    }

    // --- includeBuild() chaining (cbd-9) ---

    private fun extensionWithRepos(vararg names: String): Pair<WrkxExtension, List<WrkxRepo>> {
        val ext = WrkxExtension()
        ext.includeAction = {} // no-op for unit tests
        val repos = names.map { name ->
            WrkxRepo("org/$name", "cat", emptyList(), defaultEnabled = true).also {
                ext.register(name, it)
            }
        }
        return ext to repos
    }

    @Test
    fun `includeBuild adds repos to includedBuilds`() {
        val (_, repos) = extensionWithRepos("a", "b", "c")
        val (a, b, c) = repos

        a.includeBuild(b, c)

        assertEquals(2, a.includedBuilds.size)
        assertTrue(a.includedBuilds.contains(b))
        assertTrue(a.includedBuilds.contains(c))
    }

    @Test
    fun `includeBuild returns this for chaining`() {
        val (_, repos) = extensionWithRepos("a", "b")
        val (a, b) = repos

        val result = a.includeBuild(b)
        assertSame(a, result)
    }

    @Test
    fun `includeBuild can be called multiple times`() {
        val (_, repos) = extensionWithRepos("a", "b", "c")
        val (a, b, c) = repos

        a.includeBuild(b).includeBuild(c)

        assertEquals(2, a.includedBuilds.size)
    }

    @Test
    fun `includedBuilds is empty by default`() {
        val a = WrkxRepo("org/a", "cat", emptyList(), defaultEnabled = true)
        assertTrue(a.includedBuilds.isEmpty())
    }

    @Test
    fun `includedBuilds returns defensive copy`() {
        val (_, repos) = extensionWithRepos("a", "b")
        val (a, b) = repos
        a.includeBuild(b)

        val list1 = a.includedBuilds
        val list2 = a.includedBuilds
        assertEquals(list1, list2)
        assertNotSame(list1, list2)
    }

    // --- includeBuild() triggers inclusion ---

    @Test
    fun `includeBuild with no args includes self`() {
        val ext = WrkxExtension()
        val included = mutableListOf<String>()
        ext.includeAction = { included.add(it.repoName) }

        val foo = WrkxRepo("org/foo", "cat", emptyList(), defaultEnabled = true)
        ext.register("foo", foo)

        foo.includeBuild()

        assertEquals(listOf("org/foo"), included)
    }

    @Test
    fun `includeBuild with args includes self and deps`() {
        val ext = WrkxExtension()
        val included = mutableListOf<String>()
        ext.includeAction = { included.add(it.repoName) }

        val foo = WrkxRepo("org/foo", "cat", emptyList(), defaultEnabled = true)
        val bar = WrkxRepo("org/bar", "cat", emptyList(), defaultEnabled = true)
        val baz = WrkxRepo("org/baz", "cat", emptyList(), defaultEnabled = true)
        ext.register("foo", foo)
        ext.register("bar", bar)
        ext.register("baz", baz)

        foo.includeBuild(bar, baz)

        assertEquals(listOf("org/foo", "org/bar", "org/baz"), included)
    }

    @Test
    fun `includeBuild deduplicates across multiple calls`() {
        val ext = WrkxExtension()
        val included = mutableListOf<String>()
        ext.includeAction = { included.add(it.repoName) }

        val foo = WrkxRepo("org/foo", "cat", emptyList(), defaultEnabled = true)
        val bar = WrkxRepo("org/bar", "cat", emptyList(), defaultEnabled = true)
        val baz = WrkxRepo("org/baz", "cat", emptyList(), defaultEnabled = true)
        val moz = WrkxRepo("org/moz", "cat", emptyList(), defaultEnabled = true)
        ext.register("foo", foo)
        ext.register("bar", bar)
        ext.register("baz", baz)
        ext.register("moz", moz)

        // foo and baz both depend on bar
        foo.includeBuild(bar)
        baz.includeBuild(bar, moz)

        // bar should only appear once
        assertEquals(listOf("org/foo", "org/bar", "org/baz", "org/moz"), included)
    }
}
