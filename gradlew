#!/usr/bin/env sh
# Lightweight cross-platform-friendly Gradle bootstrap for XenoLevelBar.
# Downloads the pinned Gradle distribution on first use, then delegates to it.
set -eu

GRADLE_VERSION="9.7.1"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_ROOT="$SCRIPT_DIR/.gradle-bootstrap"
GRADLE_HOME="$DIST_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
ZIP_FILE="$DIST_ROOT/gradle-$GRADLE_VERSION-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$DIST_ROOT"
    echo "Gradle $GRADLE_VERSION is not cached; downloading it..."

    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 3 --output "$ZIP_FILE" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP_FILE" "$DIST_URL"
    else
        echo "Error: curl or wget is required for the first Gradle download." >&2
        exit 1
    fi

    rm -rf "$GRADLE_HOME"

    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$ZIP_FILE" -d "$DIST_ROOT"
    elif command -v jar >/dev/null 2>&1; then
        (cd "$DIST_ROOT" && jar xf "$ZIP_FILE")
    else
        echo "Error: unzip or the JDK 'jar' command is required." >&2
        exit 1
    fi

    rm -f "$ZIP_FILE"
fi

exec "$GRADLE_BIN" "$@"
