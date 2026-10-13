# VoiceBoard

Soundboard und Voicefilter für **Simple Voice Chat** – Fabric, Minecraft **26.3**.

## Funktionen

- **Soundeffekte**: eigene `.mp3`/`.wav`-Sounds abspielen, die alle in Hörweite über Simple Voice Chat hören
  - Hotkey pro Sound (z. B. F13–F24 für Streamdeck/Makro-Tasten), nochmal drücken = Stopp
  - Lautstärke pro Sound und Gesamtlautstärke
  - „Alle Sounds stoppen“ per Knopf oder eigener Taste
  - Selbst mithören (mit eigener Lautstärke)
  - Umbenennen (✎) und Entfernen (✖)
- **Voicefilter** (nur auf deine Stimme): Hoch, Tief, Roboter, Echo, Hall, Radio, Telefon – mit Stärke-Regler
- **Bibliothek**: zeigt alle Dateien im Sound-Ordner, einzeln oder alle auf einmal hinzufügen

Andere Spieler brauchen **diese Mod nicht**, nur Simple Voice Chat.

## Benutzen

1. Taste **J** öffnet das Menü (änderbar unter Optionen → Steuerung → VoiceBoard).
2. „Sound-Ordner öffnen“ → `.mp3`/`.wav` hineinlegen (Ordner: `.minecraft/voiceboard/sounds`).
3. Reiter **Bibliothek** → „Neu laden“ → „Hinzufügen“.
4. Reiter **Soundeffekte** → auf die Tasten-Schaltfläche klicken und die gewünschte Taste drücken (Esc = Taste entfernen).

Einstellungen liegen in `.minecraft/config/voiceboard.json`.

## Installieren

In den `mods`-Ordner gehören:
- `voiceboard-1.0.0.jar`
- Fabric API (für 26.3)
- Simple Voice Chat (Fabric, 26.3)

## Bauen

### Mit GitHub (ohne etwas zu installieren)
1. Neues Repository auf GitHub anlegen und den Inhalt dieses Ordners hochladen.
2. Unter **Actions** läuft automatisch „Build“.
3. Wenn er grün ist: auf den Lauf klicken → unten unter **Artifacts** „voiceboard“ herunterladen. Darin ist die `.jar`.

### Lokal
Java 25 (JDK) installieren, dann im Projektordner:

```
gradlew build        (Windows)
./gradlew build      (macOS/Linux)
```

Die fertige Datei liegt in `build/libs/voiceboard-1.0.0.jar`.
sha256:f00eda3235d74cdf580b9eca00fe5d3c3725a4797ec4560820439aa147401dbd

## Wenn der Build fehlschlägt

- Versionsnummern in `gradle.properties` mit https://fabricmc.net/develop abgleichen.
- Falls `voicechat-api` nicht gefunden wird: `voicechat_api_version` auf eine ältere 2.6.x-Version setzen.
