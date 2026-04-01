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
    ":task:srcx",
    ":task:opsx",
    ":task:wrkx",
    // Plugins
    ":plugin:claude",
    ":plugin:srcx",
    ":plugin:codex",
    ":plugin:copilot",
    ":plugin:opencode",
    ":plugin:opsx",
    ":plugin:wrkx",
)
