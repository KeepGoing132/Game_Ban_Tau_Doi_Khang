#!/bin/bash
# Sourced by the launchers. Compatible with the Bash 3.2 shipped with macOS.
BATTLESHIP_JAVA=java
BATTLESHIP_JAVAC=javac
BATTLESHIP_JAR=jar
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    BATTLESHIP_JAVA="$JAVA_HOME/bin/java"
    BATTLESHIP_JAVAC="$JAVA_HOME/bin/javac"
    BATTLESHIP_JAR="$JAVA_HOME/bin/jar"
fi

battleship_require_java() {
    if ! "$BATTLESHIP_JAVA" -version >/dev/null 2>&1; then
        printf '%s\n' 'Java was not found. Install a JDK (Temurin 21 recommended), then reopen Terminal.' \
            'Download: https://adoptium.net/temurin/releases/?version=21' >&2
        return 1
    fi
}
