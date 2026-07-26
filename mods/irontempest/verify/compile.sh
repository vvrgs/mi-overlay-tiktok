#!/usr/bin/env bash
# Stub-compile: compila TODO el Java del mod contra stubs del API de MC/Forge.
# Verifica sintaxis, imports, generics y consistencia interna del mod.
# NO verifica firmas reales del API (eso lo hace la revisión de mappings).
set -uo pipefail
cd "$(dirname "$0")/.."
rm -rf verify/build && mkdir -p verify/build
find src/main/java verify/stubs -name '*.java' | sort > verify/sources.txt
javac --release 17 -nowarn -d verify/build @verify/sources.txt 2> verify/errors.txt
status=$?
n=$(grep -c "error:" verify/errors.txt || true)
echo "javac exit=$status errores=$n"
if [ "$status" -ne 0 ]; then
  head -80 verify/errors.txt
fi
exit $status
