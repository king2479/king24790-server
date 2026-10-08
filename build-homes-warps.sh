#!/usr/bin/env bash
set -Eeuo pipefail

SERVER_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
API_JAR="$SERVER_DIR/server/cache/patched_1.8.8.jar"
SOURCE="$SERVER_DIR/plugins/homes-warps/src/main/java/net/king24790/homeswarps/HomesWarpsPlugin.java"
DESCRIPTOR="$SERVER_DIR/plugins/homes-warps/src/main/resources/plugin.yml"
OUTPUT="$SERVER_DIR/server/plugins/HomesWarps.jar"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

for tool in javac jar; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Cannot build HomesWarps: required command "%s" is missing.\n' "$tool" >&2
    exit 1
  fi
done

if [[ ! -s "$API_JAR" ]]; then
  printf 'Cannot build HomesWarps: expected Minecraft 1.8.8 API jar is missing: %s\n' "$API_JAR" >&2
  exit 1
fi

mkdir -p "$BUILD_DIR/classes" "$(dirname -- "$OUTPUT")"
if ! javac --release 8 -cp "$API_JAR" -d "$BUILD_DIR/classes" "$SOURCE"; then
  printf 'HomesWarps compilation failed; no plugin jar was installed.\n' >&2
  exit 1
fi
cp "$DESCRIPTOR" "$BUILD_DIR/classes/plugin.yml"
if ! jar cf "$BUILD_DIR/HomesWarps.jar" -C "$BUILD_DIR/classes" .; then
  printf 'Could not package HomesWarps; no plugin jar was installed.\n' >&2
  exit 1
fi
if ! jar tf "$BUILD_DIR/HomesWarps.jar" >/dev/null; then
  printf 'HomesWarps jar verification failed; no plugin jar was installed.\n' >&2
  exit 1
fi
install -m 0644 "$BUILD_DIR/HomesWarps.jar" "$OUTPUT"
printf 'Built plugin for the bundled Minecraft 1.8.8 server: %s\n' "$OUTPUT"
printf 'The running server will load it after its next restart.\n'
