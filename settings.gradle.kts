pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "openspec-gradle"

include(
    // Libraries
    ":lib:core",
    ":lib:psi",
    ":lib:arch",
    ":lib:exec",
    ":lib:generators",
    ":lib:quality",
    ":lib:adapters:copilot",
    ":lib:adapters:claude",
    ":lib:adapters:codex",
    ":lib:adapters:opencode",
    // Tasks
    ":task:code-intel",
    ":task:openspec",
    ":task:monolith",
    // Plugins
    ":plugin:code-intel",
    ":plugin:openspec",
    ":plugin:monolith",
)
