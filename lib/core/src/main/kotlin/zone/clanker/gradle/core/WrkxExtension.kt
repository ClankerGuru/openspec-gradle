package zone.clanker.gradle.core

import java.io.File

open class WrkxRepo(
    val repoName: String,
    val category: String,
    val substitutions: List<String>,
    defaultEnabled: Boolean,
    defaultSubstitute: Boolean = false,
    defaultRef: String = "main"
) {
    var enabled: Boolean = defaultEnabled
    var substitute: Boolean = defaultSubstitute
    var ref: String = defaultRef

    fun substitute(value: Boolean) {
        substitute = value
    }

    fun ref(value: String) {
        ref = value
    }

    /** The directory name derived from the repo name (last path segment). */
    val directoryName: String
        get() = RepoEntry(repoName, true, category, substitutions).directoryName

    /** Sanitized build name for use with `settings.includeBuild { name = ... }`. */
    val sanitizedBuildName: String
        get() {
            val raw = directoryName
            val sanitized = raw
                .replace(Regex("[^a-zA-Z0-9-]"), "-")
                .replace(Regex("-+"), "-")
                .trim('-')
                .lowercase()
            require(sanitized.isNotBlank()) {
                "Invalid build name after sanitization for repo '$repoName' (directoryName='$raw')"
            }
            return sanitized
        }

    /** The absolute path where this repo is (or would be) cloned. Set by the plugin. */
    var clonePath: File = File("")

    fun enable(value: Boolean) {
        enabled = value
    }

    private val _includes = mutableListOf<WrkxRepo>()

    /** Repos that this repo explicitly includes as composite builds (tree DSL). */
    val includedBuilds: List<WrkxRepo> get() = _includes.toList()

    /** Callback set by WrkxExtension to perform the actual inclusion. */
    internal var onInclude: ((WrkxRepo) -> Unit)? = null

    /**
     * Include this repo (and optionally its dependencies) as composite builds.
     * `gort.includeBuild()` includes gort itself.
     * `gort.includeBuild(libA, libB)` includes gort, libA, and libB.
     * Returns `this` for chaining.
     */
    fun includeBuild(vararg repos: WrkxRepo): WrkxRepo {
        _includes.addAll(repos)
        val include = onInclude
            ?: error("includeBuild() requires the repo to be registered via the wrkx plugin")
        include(this)
        repos.forEach { include(it) }
        return this
    }
}

open class WrkxExtension {
    internal val repos = mutableMapOf<String, WrkxRepo>()
    private val included = linkedSetOf<WrkxRepo>()

    /** Base directory for cloned repos. Set by the plugin. */
    var baseDir: File = File("")

    /** Action to include a repo as a build. Set by the plugin to call settings.includeBuild. */
    var includeAction: ((WrkxRepo) -> Unit)? = null

    /**
     * Include all enabled repos as composite builds via `settings.includeBuild`.
     * Only includes repos whose clone directory exists on disk.
     */
    fun includeEnabled() {
        val action = includeAction
            ?: error("includeEnabled() can only be called from settings.gradle.kts with the wrkx plugin applied")
        val enabled = enabledEntries()
        checkForDuplicateBuildNames(enabled)
        enabled.forEach { action(it) }
    }

    internal fun checkForDuplicateBuildNames(repos: List<WrkxRepo>) {
        val byName = repos.groupBy { it.sanitizedBuildName }
        val dupes = byName.filter { it.value.size > 1 }
        if (dupes.isNotEmpty()) {
            val details = dupes.entries.joinToString("\n") { (name, colliding) ->
                "  '$name' ← ${colliding.joinToString(", ") { it.repoName }}"
            }
            throw IllegalStateException(
                "Duplicate sanitized build names detected — Gradle requires unique build names:\n$details"
            )
        }
    }

    fun register(propertyName: String, repo: WrkxRepo) {
        require(propertyName !in repos) {
            "Repo '$propertyName' is already registered"
        }
        repos[propertyName] = repo
        repo.onInclude = { target ->
            if (included.add(target)) {
                val action = includeAction
                    ?: error("includeBuild() requires includeAction to be set — ensure the wrkx plugin is applied in settings.gradle.kts")
                action(target)
            }
        }
    }

    operator fun get(name: String): WrkxRepo =
        repos[name] ?: throw IllegalArgumentException(
            "Unknown repo '$name'. Available: ${repos.keys.sorted().joinToString()}"
        )

    fun allEntries(): Collection<WrkxRepo> = repos.values

    fun enabledEntries(): List<WrkxRepo> = repos.values.filter { it.enabled }

    /**
     * Include a tree of repos as composite builds. The root is the host project (not included itself).
     * Walks the tree depth-first, deduplicates, checks for name collisions, and includes each repo.
     */
    fun includeTree(root: WrkxRepo) {
        val action = includeAction
            ?: error("includeTree() can only be called from settings.gradle.kts with the wrkx plugin applied")

        val visited = linkedSetOf<WrkxRepo>()
        fun collect(repo: WrkxRepo) {
            if (visited.add(repo)) {
                repo.includedBuilds.forEach { collect(it) }
            }
        }
        visited.add(root)
        root.includedBuilds.forEach { collect(it) }

        val reposToInclude = visited.filter { it !== root }
        checkForDuplicateBuildNames(reposToInclude)
        reposToInclude.forEach { action(it) }
    }

    companion object {
        fun toCamelCase(input: String): String {
            val parts = input.split(Regex("[-_ ]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return input
            return parts[0].lowercase() + parts.drop(1).joinToString("") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }
        }
    }
}
