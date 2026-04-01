#!/usr/bin/env bash
# OpenSpec Gradle — one-line installer
# Usage: curl -fsSL https://raw.githubusercontent.com/ClankerGuru/openspec-gradle/main/install.sh | bash
#
# Installs init scripts to ~/.gradle/init.d/ so every Gradle project gets
# OpenSpec tasks automatically. No per-project configuration needed.
#
# To uninstall: rm ~/.gradle/init.d/0*-*.init.gradle.kts

set -euo pipefail

VERSION="${OPENSPEC_VERSION:-0.33.0}"
INIT_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"
PROPS_FILE="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

mkdir -p "$INIT_DIR"

echo "Installing OpenSpec Gradle v${VERSION}..."

# ── 00-wrkx: Workspace management (clones repos, wires includeBuild) ──
cat > "$INIT_DIR/00-wrkx.init.gradle.kts" << WRKX
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-wrkx:$VERSION")
        classpath("zone.clanker:wrkx-tasks:$VERSION")
        classpath("zone.clanker:openspec-core:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.wrkx.WrkxPlugin>() }
WRKX
echo "  [00] wrkx — workspace management"

# ── 01-srcx: Source intelligence (discovery, analysis, refactoring) ──
cat > "$INIT_DIR/01-srcx.init.gradle.kts" << SRCX
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-srcx:$VERSION")
        classpath("zone.clanker:srcx-tasks:$VERSION")
        classpath("zone.clanker:openspec-core:$VERSION")
        classpath("zone.clanker:openspec-psi:$VERSION")
        classpath("zone.clanker:openspec-arch:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.srcx.SrcxPlugin>() }
SRCX
echo "  [01] srcx — source intelligence"

# ── 02-opsx: Workflow engine (proposals, exec, sync, dashboard) ──
cat > "$INIT_DIR/02-opsx.init.gradle.kts" << OPSX
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-opsx:$VERSION")
        classpath("zone.clanker:opsx-tasks:$VERSION")
        classpath("zone.clanker:openspec-core:$VERSION")
        classpath("zone.clanker:openspec-exec:$VERSION")
        classpath("zone.clanker:openspec-generators:$VERSION")
        classpath("zone.clanker:openspec-adapter-claude:$VERSION")
        classpath("zone.clanker:openspec-adapter-copilot:$VERSION")
        classpath("zone.clanker:openspec-adapter-codex:$VERSION")
        classpath("zone.clanker:openspec-adapter-opencode:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.opsx.OpsxPlugin>() }
allprojects {
    buildscript {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
        dependencies {
            classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
            classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
        }
    }
    afterEvaluate {
        val lintingEnabled = System.getProperty("openspec.linting.enabled") != "false"
        if (!lintingEnabled) return@afterEvaluate
        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        if (isKotlinProject) {
            apply<zone.clanker.gradle.linting.OpenSpecLintingPlugin>()
        }
    }
}
OPSX
echo "  [02] opsx — workflow engine"

# ── 03-claude: Claude Code CLI wrapper ──
cat > "$INIT_DIR/03-claude.init.gradle.kts" << CLAUDE
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-claude:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.claude.ClaudePlugin>() }
CLAUDE
echo "  [03] claude — Claude Code CLI"

# ── Default agent config ──
if [ -f "$PROPS_FILE" ]; then
    if ! grep -q "zone.clanker.opsx.agents" "$PROPS_FILE" 2>/dev/null; then
        echo "" >> "$PROPS_FILE"
        echo "# OpenSpec: which agents to generate files for (github, claude, codex, opencode, none)" >> "$PROPS_FILE"
        echo "zone.clanker.opsx.agents=claude" >> "$PROPS_FILE"
    fi
else
    cat > "$PROPS_FILE" << PROPS
# OpenSpec: which agents to generate files for (github, claude, codex, opencode, none)
zone.clanker.opsx.agents=claude
PROPS
fi

# ── Remove old monolithic init script if present ──
OLD_SCRIPT="$INIT_DIR/openspec.init.gradle.kts"
if [ -f "$OLD_SCRIPT" ]; then
    rm "$OLD_SCRIPT"
    echo "  Removed old monolithic init script"
fi

echo ""
echo "Done! OpenSpec v${VERSION} installed."
echo ""
echo "  Init scripts: $INIT_DIR/0*.init.gradle.kts"
echo "  Agent config: $PROPS_FILE"
echo ""
echo "  Run in any Gradle project:"
echo "    ./gradlew srcx     # list source intelligence tasks"
echo "    ./gradlew opsx     # list workflow tasks"
echo "    ./gradlew wrkx     # list workspace tasks"
echo "    ./gradlew claude   # list Claude CLI tasks"
echo ""
echo "  To add more agents:"
echo "    curl -fsSL .../install-copilot.sh | bash"
echo ""
echo "  To uninstall:"
echo "    rm $INIT_DIR/0*-*.init.gradle.kts"
