package zone.clanker.gradle.core

object VersionInfo {
    val PLUGIN_VERSION: String by lazy {
        VersionInfo::class.java.classLoader
            .getResourceAsStream("openspec-gradle.properties")
            ?.let { java.util.Properties().apply { load(it) }.getProperty("version") }
            ?: "0.0.0"
    }
}
