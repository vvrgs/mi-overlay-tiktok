#!/usr/bin/env bash
# Stub-compile del mod Cataclismo: verifica TODO el codigo Java contra stubs
# con las firmas REALES de Forge 1.20.1 (mojmap). 0 errores tras cada bloque
# de trabajo. Esto NO produce un jar: el build real es .\gradlew build en
# Windows con los maven de Forge accesibles.
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=verify/out
rm -rf "$OUT"
mkdir -p "$OUT"

SOURCES=$(find src/main/java verify/stubs -name '*.java')

# shellcheck disable=SC2086
javac --release 17 -proc:none -Xlint:none -d "$OUT" $SOURCES

echo "COMPILACION OK ($(echo "$SOURCES" | wc -l) archivos)"
