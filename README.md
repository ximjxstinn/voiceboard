# VoiceBoard

Soundboard und Voicefilter für **Simple Voice Chat** – Fabric, Minecraft **1.21.11** und **26.1 – 26.3**.

Fragen?
Discord: xImJxstinn
Twitch.tv/ximjxstinn

## Funktionen

- **Soundeffekte**: eigene `.mp3`/`.wav`-Sounds abspielen, die alle in Hörweite über Simple Voice Chat hören
  - Hotkey pro Sound (z. B. F13–F24 für Streamdeck/Makro-Tasten), nochmal drücken = **Pause**, nochmal = weiter an derselben Stelle
  - **Mini-Player** oben: « 10 s zurück, Pause/Weiter, 10 s vor » und ein Spulregler mit Zeit (z. B. 1:23 / 3:45) – ziehen oder klicken springt an die Stelle
  - Pausierte Sounds: gelber ▶-Knopf, gelber Fortschrittsbalken und „pausiert“
  - Lautstärke pro Sound und Gesamtlautstärke – **bis 200 %** (über 100 % orange markiert, mit sanftem Begrenzer gegen Übersteuern)
  - „Alle Sounds stoppen“ per Knopf oder eigener Taste
  - Selbst mithören (mit eigener Lautstärke)
  - Symbole unten links neben dem Mikrofon: Musiknote, solange ein Sound läuft, und Smiley-Maske, solange ein Voicefilter aktiv ist (abschaltbar mit „Anzeige“)
  - Loop-Modus pro Sound – Knopf **„↻ Loop“**: grün = an, dunkelgrau = aus
  - Umbenennen (✎) und Entfernen (✖)
- **Voicefilter-Schalter „Voicefilter: An/Aus“** mit eigener Taste – schaltet den Filter aus, ohne die Auswahl zu verlieren
- **Voicefilter** (nur auf deine Stimme): Hoch, Tief, Baby, Roboter, Echo, Hall, Radio, Telefon – mit Stärke-Regler
  und eigenen Reglern pro Filter (Tonhöhe, Frequenz, Verzögerung/Wiederholung, Raumgröße, Verzerrung, Qualität)
- **Bibliothek**: zeigt alle Dateien im Sound-Ordner, einzeln oder alle auf einmal hinzufügen
  - **Sounds per Link herunterladen**: YouTube, TikTok, SoundCloud usw. oder direkte .mp3/.wav-Links.
    Beim ersten Mal lädt die Mod dafür automatisch yt-dlp und ffmpeg (Windows ca. 100 MB) nach
    `.minecraft/voiceboard/tools`. Unter Linux muss ffmpeg installiert sein.

- **Soundboard-Knopf im Voice-Chat-Menü** (Taste V): Musiknoten-Icon unten neben „Lautstärken“ öffnet VoiceBoard
- **Mod Menu**: Mit installiertem [Mod Menu](https://modrinth.com/mod/modmenu) öffnet der Knopf „Konfigurieren“ (Zahnrad) das VoiceBoard-Menü – auch im Hauptmenü ohne Welt. Mod Menu ist optional.

Andere Spieler brauchen **diese Mod nicht**, nur Simple Voice Chat.

## Benutzen

1. Taste **J** öffnet das Menü (änderbar unter Optionen → Steuerung → VoiceBoard).
2. „Sound-Ordner öffnen“ → `.mp3`/`.wav` hineinlegen (Ordner: `.minecraft/voiceboard/sounds`).
3. Reiter **Bibliothek** → „Neu laden“ → „Hinzufügen“.
4. Reiter **Soundeffekte** → auf die Tasten-Schaltfläche klicken und die gewünschte Taste drücken (Esc = Taste entfernen).

### Gemeinsamer Speicher für alle Versionen
Sounds, Soundliste, Lautstärken, Loop, Namen, Voicefilter und die Download-Werkzeuge liegen in **einem**
Ordner, den alle Minecraft-Versionen (1.21.11, 26.1 – 26.3) und alle Launcher-Profile gemeinsam nutzen:

| System | Ordner |
|---|---|
| Windows | `%APPDATA%\VoiceBoard` |
| macOS | `~/Library/Application Support/VoiceBoard` |
| Linux | `~/.config/voiceboard` |

Sounds aus älteren Versionen (Ordner `voiceboard/sounds` im Minecraft-Profil) werden beim ersten Start
automatisch übernommen. Die alten Dateien bleiben dabei unverändert.

**Tasten:** 26.3 nutzt ein anderes Tastensystem als 1.21.11/26.1/26.2. Deshalb wird pro Sound je eine Belegung für
„1.21.11/26.1/26.2“ und „26.3“ gespeichert. Ist eine Taste nur in der anderen Version belegt, steht dort **„Neu belegen“**.

## Installieren

In den `mods`-Ordner gehören:
- `voiceboard-1.4.3.jar` – **eine Datei für Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 und 26.3**
- Fabric API (passend zu deiner Minecraft-Version)
- Simple Voice Chat (Fabric, passend zu deiner Minecraft-Version)

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

Ein `gradlew build` erzeugt **eine** Jar für alle Versionen: `build/libs/voiceboard-1.4.3.jar`.

### Aufbau
- `src/core/` – Kern **ohne Minecraft-Code**: Audio, Voicefilter, Einstellungen, Simple-Voice-Chat-Plugin und der Starter `VoiceBoardEntry`
- `src/client/` – Menüs, Tasten, HUD. Wird für jede Version einzeln gegen deren Minecraft kompiliert und dabei in ein eigenes Paket verschoben (`mc1_21_11`, `mc26_1`, `mc26_2`, `mc26_3`)
- `src/compat/<ordner>/` – die wenigen Stellen, die sich zwischen den Versionen unterscheiden
  (für 1.21.11 zusätzlich `replacements.txt`: Namen, die dort anders heißen, z. B. `GuiGraphics` statt `GuiGraphicsExtractor`)
- 1.21.11 ist noch verschleiert: Diese Variante wird mit Mojang-Namen gebaut und von Loom umgewandelt; ihr Kern ist mit Java 21 gebaut
- `versions/<version>/version.properties` – Minecraft- und Fabric-API-Version je Variante

Beim Spielstart liest `VoiceBoardEntry` die Minecraft-Version und lädt **nur** die passende Variante.

Neue Minecraft-Version: Ordner in `versions/` anlegen, Compat-Datei ergänzen und in `VoiceBoardEntry.variantFor` eintragen.

## Wenn der Build fehlschlägt

- Versionsnummern in `gradle.properties` mit https://fabricmc.net/develop abgleichen.
- Falls `voicechat-api` nicht gefunden wird: `voicechat_api_version` auf eine ältere 2.6.x-Version setzen.
