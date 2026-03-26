package zone.clanker.gradle.core

open class MonolithRepo(
    val repoName: String,
    val category: String,
    val substitutions: List<String>,
    defaultEnabled: Boolean
) {
    var enabled: Boolean = defaultEnabled

    fun enable(value: Boolean) {
        enabled = value
    }
}

open class MonolithExtension {
    internal val repos = mutableMapOf<String, MonolithRepo>()

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
