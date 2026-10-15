#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
source ./scripts/java_env.sh
battleship_require_java
if [ ! -f bin/client/ClientMain.class ]; then
    /bin/bash ./compile.sh
fi
exec "$BATTLESHIP_JAVA" -Dfile.encoding=UTF-8 -cp bin client.ClientMain
