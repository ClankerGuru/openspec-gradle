#!/usr/bin/env bash
# OpenSpec Gradle — one-line installer
# Usage: curl -fsSL https://raw.githubusercontent.com/ClankerGuru/openspec-gradle/main/install.sh | bash
#
# Non-interactive: OPENSPEC_COMPONENTS=srcx,opsx,claude curl ... | bash
# To uninstall: rm ~/.gradle/init.d/0*-*.init.gradle.kts

set -euo pipefail

VERSION="${OPENSPEC_VERSION:-0.34.3}"
INIT_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"
PROPS_FILE="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

mkdir -p "$INIT_DIR"

echo ""
echo "  OpenSpec Gradle v${VERSION}"
echo "  ─────────────────────────────"
echo ""

# ── Component writers ──

write_wrkx() {
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
}

write_srcx() {
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
}

write_opsx() {
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
OPSX
}

write_quality() {
    cat > "$INIT_DIR/02-quality.init.gradle.kts" << QUALITY
// Linting: detekt + ktlint for Kotlin projects
// Disable all: -Dopenspec.linting.enabled=false
// Disable detekt only: -Dopenspec.detekt.enabled=false
// Disable ktlint only: -Dopenspec.ktlint.enabled=false
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:quality:$VERSION")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    }
}
allprojects {
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
QUALITY
}

write_agent() {
    local artifact="$1" class="$2" file="$3"
    cat > "$INIT_DIR/$file" << AGENT
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:$artifact:$VERSION")
    }
}
beforeSettings { apply<$class>() }
AGENT
}

# ── Normalize input ──
normalize_selection() {
    local input="$1"
    case "$input" in
        9|all)  echo "wrkx,srcx,opsx,quality,claude,copilot,codex,opencode" ;;
        none)   echo "none" ;;
        1)      echo "srcx" ;;
        2)      echo "opsx" ;;
        3)      echo "wrkx" ;;
        4)      echo "quality" ;;
        5)      echo "claude" ;;
        6)      echo "copilot" ;;
        7)      echo "codex" ;;
        8)      echo "opencode" ;;
        *)      echo "$input" ;;
    esac
}

# ── Selection ──
if [ -n "${OPENSPEC_COMPONENTS:-}" ]; then
    SELECTED=$(normalize_selection "$OPENSPEC_COMPONENTS")
elif [ -t 0 ]; then
    echo "  Select components to install (comma-separated numbers, names, or 'all'):"
    echo ""
    echo "  Core:"
    echo "    1) srcx      — source intelligence (discovery, analysis, refactoring)"
    echo "    2) opsx      — workflow engine (proposals, exec, agent sync)"
    echo "    3) wrkx      — workspace management (multi-repo, composite builds)"
    echo "    4) quality   — linting (detekt + ktlint for Kotlin projects)"
    echo ""
    echo "  Agents:"
    echo "    5) claude    — Claude Code CLI wrapper"
    echo "    6) copilot   — GitHub Copilot CLI wrapper"
    echo "    7) codex     — OpenAI Codex CLI wrapper"
    echo "    8) opencode  — OpenCode CLI wrapper"
    echo ""
    echo "    9) all       — Install everything"
    echo ""
    printf "  Select: "
    read -r CHOICE
    SELECTED=$(normalize_selection "$CHOICE")
else
    echo "  Non-interactive mode — installing all."
    SELECTED="wrkx,srcx,opsx,quality,claude,copilot,codex,opencode"
fi

if [ "$SELECTED" = "none" ]; then
    echo "  No components selected. Nothing to install."
    exit 0
fi

# ── Clean previous install (re-installs are idempotent, not additive) ──
rm -f "$INIT_DIR"/0*-*.init.gradle.kts

echo ""
echo "  Installing..."

INSTALLED_AGENTS=""

IFS=',' read -ra COMPONENTS <<< "$SELECTED"
for comp in "${COMPONENTS[@]}"; do
    comp=$(echo "$comp" | xargs)
    case "$comp" in
        srcx)
            write_srcx
            echo "  [01] srcx — source intelligence"
            ;;
        opsx)
            write_opsx
            echo "  [02] opsx — workflow engine"
            ;;
        wrkx)
            write_wrkx
            echo "  [00] wrkx — workspace management"
            ;;
        quality)
            write_quality
            echo "  [02] quality — linting (detekt + ktlint)"
            ;;
        claude)
            write_agent "plugin-claude" "zone.clanker.claude.ClaudePlugin" "03-claude.init.gradle.kts"
            echo "  [03] claude — Claude Code CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}claude"
            ;;
        copilot|github|github-copilot)
            write_agent "plugin-copilot" "zone.clanker.copilot.CopilotPlugin" "03-copilot.init.gradle.kts"
            echo "  [03] copilot — GitHub Copilot CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}github"
            ;;
        codex)
            write_agent "plugin-codex" "zone.clanker.codex.CodexPlugin" "03-codex.init.gradle.kts"
            echo "  [03] codex — OpenAI Codex CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}codex"
            ;;
        opencode)
            write_agent "plugin-opencode" "zone.clanker.opencode.OpencodePlugin" "03-opencode.init.gradle.kts"
            echo "  [03] opencode — OpenCode CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}opencode"
            ;;
        *)
            echo "  Unknown component: $comp (skipping)"
            ;;
    esac
done

# ── Set agent config if opsx installed ──
if [ -f "$INIT_DIR/02-opsx.init.gradle.kts" ]; then
    AGENTS_VALUE="${INSTALLED_AGENTS:-none}"
    if [ -f "$PROPS_FILE" ] && grep -q "zone.clanker.opsx.agents" "$PROPS_FILE" 2>/dev/null; then
        sed -i.bak "s/zone.clanker.opsx.agents=.*/zone.clanker.opsx.agents=$AGENTS_VALUE/" "$PROPS_FILE"
        rm -f "${PROPS_FILE}.bak"
    elif [ -f "$PROPS_FILE" ]; then
        echo "" >> "$PROPS_FILE"
        echo "zone.clanker.opsx.agents=$AGENTS_VALUE" >> "$PROPS_FILE"
    else
        echo "zone.clanker.opsx.agents=$AGENTS_VALUE" > "$PROPS_FILE"
    fi
fi

# ── Remove legacy init script ──
rm -f "$INIT_DIR/openspec.init.gradle.kts" "$INIT_DIR/openspec.init.gradle.kts.bak"

echo ""
echo "  Done! OpenSpec v${VERSION}"
echo ""
echo "  Init scripts:"
ls -1 "$INIT_DIR"/0*.init.gradle.kts 2>/dev/null | while read -r f; do echo "    $(basename "$f")"; done
echo ""
echo "  Run in any Gradle project:"
[ -f "$INIT_DIR/01-srcx.init.gradle.kts" ] && echo "    ./gradlew srcx       # source intelligence"
[ -f "$INIT_DIR/02-opsx.init.gradle.kts" ] && echo "    ./gradlew opsx       # workflow engine"
[ -f "$INIT_DIR/00-wrkx.init.gradle.kts" ] && echo "    ./gradlew wrkx       # workspace management"
[ -f "$INIT_DIR/03-claude.init.gradle.kts" ] && echo "    ./gradlew claude     # Claude Code CLI"
[ -f "$INIT_DIR/03-copilot.init.gradle.kts" ] && echo "    ./gradlew copilot    # GitHub Copilot CLI"
[ -f "$INIT_DIR/03-codex.init.gradle.kts" ] && echo "    ./gradlew codex      # OpenAI Codex CLI"
[ -f "$INIT_DIR/03-opencode.init.gradle.kts" ] && echo "    ./gradlew opencode   # OpenCode CLI"
echo ""
echo "  To uninstall: rm $INIT_DIR/0*-*.init.gradle.kts"
