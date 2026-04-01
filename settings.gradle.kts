pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "openspec-gradle"

include(
    ":core",
    ":psi",
    ":arch",
    ":exec",
    ":generators",
    ":adapters:copilot",
    ":adapters:claude",
    ":adapters:codex",
    ":adapters:opencode",
    ":tasks",
    ":quality",
    ":plugin",
    // New split modules
    ":gradle-tasks",
    ":openspec-tasks",
    ":monolith-tasks",
    // New plugin modules
    ":plugin-gradle",
    ":plugin-openspec",
    ":plugin-monolith",
)
