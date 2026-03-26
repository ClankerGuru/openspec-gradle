package zone.clanker.gradle.core

import java.io.File

open class MonolithRepo(
    val repoName: String,
    val category: String,
    val substitutions: List<String>,
    defaultEnabled: Boolean
) {
    var enabled: Boolean = defaultEnabled

    /** The directory name derived from the repo name (last path segment). */
    val directoryName: String
        get() = RepoEntry(repoName, true, category, substitutions).directoryName

    /** The absolute path where this repo is (or would be) cloned. Set by the plugin. */
    var clonePath: File = File("")

    fun enable(value: Boolean) {
        enabled = value
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
        enabledEntries().forEach { action(it) }
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

    companion object {
        fun toCamelCase(kebab: String): String {
            val parts = kebab.split("-")
            if (parts.isEmpty()) return kebab
            return parts[0] + parts.drop(1).joinToString("") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }
        }
    }
}
