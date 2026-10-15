#!/usr/bin/env bash
# Baut VoiceBoard (eine Jar für Minecraft 26.1 – 26.3) und legt sie in out/.
# Bei Fehlern werden die Compiler-Meldungen als GitHub-Hinweise ausgegeben,
# damit man sie direkt auf der Actions-Seite sieht.
set -uo pipefail
cd "$(dirname "$0")"
mkdir -p out
if gradle clean build --stacktrace 2>&1 | tee build.log; then
  cp build/libs/*.jar out/
  echo "Fertige Jar:"; ls -1 out
  exit 0
fi
grep -E "\.java:[0-9]+: error:" build.log | head -20 | while IFS= read -r line; do
  echo "::error::${line}"
done
awk '/What went wrong/{f=1;c=0} f&&c<8{print "::error::" $0; c++}' build.log | head -16
exit 1
