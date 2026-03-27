package zone.clanker.gradle.core

import java.io.File

open class MonolithRepo(
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

    private val _includes = mutableListOf<MonolithRepo>()

    /** Repos that this repo explicitly includes as composite builds (tree DSL). */
    val includedBuilds: List<MonolithRepo> get() = _includes.toList()

    /** Declare that this repo includes other repos as composite builds. Returns `this` for chaining. */
    fun includeBuild(vararg repos: MonolithRepo): MonolithRepo {
        _includes.addAll(repos)
        return this
    }
}

open class MonolithExtension {
    internal val repos = mutableMapOf<String, MonolithRepo>()

    /** Base directory for cloned repos. Set by the plugin. */
    var baseDir: File = File("")

    /** Action to include a repo as a build. Set by the plugin to call settings.includeBuild. */
    var includeAction: ((MonolithRepo) -> Unit)? = null

    /**
     * Include all enabled repos as composite builds via `settings.includeBuild`.
     * Only includes repos whose clone directory exists on disk.
     */
    fun includeEnabled() {
        val action = includeAction
            ?: error("includeEnabled() can only be called from settings.gradle.kts with the monolith plugin applied")
        val enabled = enabledEntries()
        checkForDuplicateBuildNames(enabled)
        enabled.forEach { action(it) }
    }

    internal fun checkForDuplicateBuildNames(repos: List<MonolithRepo>) {
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

    fun register(propertyName: String, repo: MonolithRepo) {
        require(propertyName !in repos) {
            "Repo '$propertyName' is already registered"
        }
        repos[propertyName] = repo
    }

    operator fun get(name: String): MonolithRepo =
        repos[name] ?: throw IllegalArgumentException(
            "Unknown repo '$name'. Available: ${repos.keys.sorted().joinToString()}"
        )

    fun allEntries(): Collection<MonolithRepo> = repos.values

    fun enabledEntries(): List<MonolithRepo> = repos.values.filter { it.enabled }

    /**
     * Include a tree of repos as composite builds. The root is the host project (not included itself).
     * Walks the tree depth-first, deduplicates, checks for name collisions, and includes each repo.
     */
    fun includeTree(root: MonolithRepo) {
        val action = includeAction
            ?: error("includeTree() can only be called from settings.gradle.kts with the monolith plugin applied")

        val visited = linkedSetOf<MonolithRepo>()
        fun collect(repo: MonolithRepo) {
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
