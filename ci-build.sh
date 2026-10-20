#!/usr/bin/env bash
# Baut VoiceBoard (eine Jar für Minecraft 1.21.11 und 26.1 – 26.3) und legt sie in out/.
# Bei Fehlern werden die Compiler-Meldungen als GitHub-Hinweise ausgegeben,
# damit man sie direkt auf der Actions-Seite sieht.
set -uo pipefail
cd "$(dirname "$0")"
mkdir -p out
if gradle clean build --stacktrace 2>&1 | tee build.log; then
  cp build/libs/*.jar out/
  echo "Fertige Jar:"; ls -1 out
  # Kontrolle: stecken alle Minecraft-Varianten in der Jar?
  for jar in out/*.jar; do
    for v in versions/*/; do
      variant="mc$(basename "$v" | tr '.' '_')"
      n=$(unzip -l "$jar" | grep -c "de/ximjxstinn/voiceboard/${variant}/.*\.class" || true)
      if [ "$n" -gt 0 ]; then
        echo "::notice::$(basename "$jar"): Variante ${variant} mit ${n} Klassen enthalten"
      else
        echo "::error::$(basename "$jar"): Variante ${variant} FEHLT"
        exit 1
      fi
    done
    # Kontrolle 1.21.11: Kern muss Java 21 sein (Klassenversion 65), sonst startet die Mod dort nicht
    tmp=$(mktemp -d); unzip -q "$jar" -d "$tmp"
    ver=$(javap -v "$tmp/de/ximjxstinn/voiceboard/VoiceBoardEntry.class" | awk '/major version/{print $3}')
    if [ "$ver" = "65" ]; then echo "::notice::Kern ist Java 21 (Klassenversion $ver)"; else echo "::error::Kern hat Klassenversion $ver statt 65 (Java 21)"; exit 1; fi
    # Kontrolle 1.21.11: Variante muss umgewandelt sein (keine Mojang-Namen mehr, sondern intermediary)
    if strings "$tmp"/de/ximjxstinn/voiceboard/mc1_21_11/*.class | grep -q "net/minecraft/client/Minecraft\b"; then
      echo "::error::Variante mc1_21_11 ist nicht umgewandelt (Mojang-Namen gefunden)"; exit 1
    fi
    n=$(strings "$tmp"/de/ximjxstinn/voiceboard/mc1_21_11/*.class | grep -c "net/minecraft/class_" || true)
    echo "::notice::Variante mc1_21_11 umgewandelt (${n} intermediary-Verweise)"
    echo "::notice::fabric.mod.json: $(tr -d '\n\t' < "$tmp/fabric.mod.json" | grep -o '"depends".*' | cut -c1-200)"
    echo "::notice::Eingebettet: $(ls "$tmp"/META-INF/jars 2>/dev/null | tr '\n' ' ')"
    rm -rf "$tmp"
    if unzip -l "$jar" | grep -q "com/terraformersmc/"; then
      echo "::error::$(basename "$jar"): Mod-Menu-Nachbau darf nicht in der Jar sein"
      exit 1
    fi
  done
  exit 0
fi
grep -E "\.java:[0-9]+: error:" build.log | head -20 | while IFS= read -r line; do
  echo "::error::${line}"
done
awk '/What went wrong/{f=1;c=0} f&&c<8{print "::error::" $0; c++}' build.log | head -16
exit 1
