#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
source ./scripts/java_env.sh
battleship_require_java

if ! "$BATTLESHIP_JAVAC" -version >/dev/null 2>&1 || ! command -v "$BATTLESHIP_JAR" >/dev/null 2>&1; then
    printf '%s\n' 'Building requires a full JDK with javac and jar (a runtime alone is not enough).' >&2
    exit 1
fi

mkdir -p bin dist
sources=()
while IFS= read -r -d '' source_file; do
    sources+=("$source_file")
done < <(find src -type f -name '*.java' -print0)
if [ "${#sources[@]}" -eq 0 ]; then
    printf '%s\n' 'No Java sources found in src/.' >&2
    exit 1
fi

flags=(-encoding UTF-8 -d bin)
version=$("$BATTLESHIP_JAVAC" -version 2>&1)
if [[ "$version" != javac\ 1.8.* ]]; then
    flags+=(--release 8 -Xlint:-options)
fi
"$BATTLESHIP_JAVAC" "${flags[@]}" "${sources[@]}"
"$BATTLESHIP_JAR" cfe dist/battleship-client.jar client.ClientMain -C bin client -C bin common
mkdir -p dist/lib
cp lib/*.jar lib/*LICENSE* dist/lib/
cp THIRD_PARTY_NOTICES.md dist/
manifest=$(mktemp)
printf 'Main-Class: server.ServerMain\nClass-Path: lib/sqlite-jdbc-3.53.4.0.jar\n\n' > "$manifest"
"$BATTLESHIP_JAR" cfm dist/battleship-server.jar "$manifest" -C bin server -C bin common
rm -f "$manifest"
printf '%s\n' 'Build OK (Java 8 API compatibility).' 'Portable JARs: dist/battleship-client.jar and dist/battleship-server.jar'
