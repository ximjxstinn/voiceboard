package de.ximjxstinn.voiceboard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.ximjxstinn.voiceboard.VoiceBoard;
import de.ximjxstinn.voiceboard.audio.FilterSettings;
import de.ximjxstinn.voiceboard.audio.FilterType;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Alle Einstellungen der Mod. Liegen im gemeinsamen Ordner (siehe {@link Storage}),
 * damit jede Minecraft-Version und jedes Launcher-Profil dieselben Sounds sieht.
 */
public class BoardConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ob die laufende Minecraft-Version das neue Tastensystem (SDL, ab 26.3) nutzt. */
    public static boolean sdlKeys;

    /** Höchste Lautstärke in Prozent (über 100 % = verstärkt). */
    public static final int MAX_VOLUME = 200;

    /** Format der Tastenbelegungen (2 = getrennt nach altem und neuem Tastensystem). */
    public int keyFormat = 2;

    /** Lautstärke aller Sounds für die anderen Spieler (0-100). */
    public int masterVolume = 70;
    /** Ob du deine eigenen Sounds lokal mithörst. */
    public boolean hearSelf = true;
    /** Lautstärke beim Mithören (0-100). */
    public int monitorVolume = 50;
    /** Taste für "Alle Sounds stoppen". */
    public KeyBind stopAllKey = new KeyBind();
    /** Ob Sounds auch gesendet werden, wenn das Mikrofon stumm ist. */
    public boolean playWhileMuted = true;
    /** Anzeige unten links, solange ein Sound läuft. */
    public boolean showHud = true;

    public FilterType filter = FilterType.NONE;
    /** Hauptschalter für den Voicefilter (der gewählte Filter bleibt gespeichert). */
    public boolean filterEnabled = true;
    /** Taste zum Ein-/Ausschalten des Voicefilters. */
    public KeyBind filterToggleKey = new KeyBind();
    /** Stärke des Voicefilters (0-100). */
    public int filterStrength = 100;
    /** Einstellungen der einzelnen Filter. */
    public FilterSettings filterSettings = new FilterSettings();

    public List<SoundEntry> sounds = new ArrayList<>();

    private transient boolean dirty;

    public static class SoundEntry {
        public String file;
        public String name;
        public int volume = 100;
        /** Sound endlos wiederholen. */
        public boolean loop;
        public KeyBind key = new KeyBind();

        public SoundEntry() {
        }

        public SoundEntry(String file, String name) {
            this.file = file;
            this.name = name;
        }
    }

    /**
     * Eine Tastenbelegung. 26.1/26.2 (GLFW) und 26.3 (SDL) haben verschiedene Tastencodes,
     * deshalb wird für jedes Tastensystem eine eigene Belegung gespeichert.
     */
    public static class KeyBind {
        /** Belegung für 26.1 / 26.2 (altes Tastensystem). */
        public String type;
        public int code = -1;
        /** Belegung für 26.3 (neues Tastensystem). */
        public String sdlType;
        public int sdlCode = -1;

        public String type() {
            return sdlKeys ? sdlType : type;
        }

        public int code() {
            return sdlKeys ? sdlCode : code;
        }

        public void set(String newType, int newCode) {
            if (sdlKeys) {
                sdlType = newType;
                sdlCode = newCode;
            } else {
                type = newType;
                code = newCode;
            }
        }

        public boolean isBound() {
            return type() != null && code() != -1;
        }

        /** Nur im jeweils anderen Tastensystem belegt (z. B. in 26.3, aber noch nicht für 26.1). */
        public boolean isBoundOnlyElsewhere() {
            boolean other = sdlKeys ? (type != null && code != -1) : (sdlType != null && sdlCode != -1);
            return !isBound() && other;
        }

        public void clear() {
            set(null, -1);
        }

        /** Übernimmt Belegungen aus einer anderen Datei, wo hier noch nichts belegt ist. */
        void mergeFrom(KeyBind other) {
            if ((type == null || code == -1) && other.type != null) {
                type = other.type;
                code = other.code;
            }
            if ((sdlType == null || sdlCode == -1) && other.sdlType != null) {
                sdlType = other.sdlType;
                sdlCode = other.sdlCode;
            }
        }

        /** Alte Dateien kannten nur ein Feld – das gehört zum System, das sie geschrieben hat. */
        void upgradeFromSingle(boolean writtenWithSdl) {
            if (writtenWithSdl && type != null) {
                sdlType = type;
                sdlCode = code;
                type = null;
                code = -1;
            }
        }
    }

    public static Path file() {
        return Storage.configFile();
    }

    public static Path soundFolder() {
        return Storage.soundFolder();
    }

    public static BoardConfig load() {
        BoardConfig config = read(file());
        boolean importLegacy = Storage.shouldImportLegacy();
        if (config == null) {
            config = importLegacy ? read(Storage.legacyConfigFile()) : null;
            if (config == null) {
                config = new BoardConfig();
            }
        } else if (importLegacy) {
            // Gemeinsame Liste gibt es schon: Sounds aus diesem Profil ergänzen
            BoardConfig legacy = read(Storage.legacyConfigFile());
            if (legacy != null) {
                for (SoundEntry s : legacy.sounds) {
                    if (s == null || s.file == null) continue;
                    SoundEntry existing = config.find(s.file);
                    if (existing == null) {
                        config.sounds.add(s);
                    } else if (s.key != null && existing.key != null) {
                        existing.key.mergeFrom(s.key);
                    }
                }
            }
        }
        if (importLegacy) {
            Storage.copyLegacySounds();
        }
        config.sanitize();
        try {
            Files.createDirectories(soundFolder());
        } catch (Exception e) {
            VoiceBoard.LOGGER.error("Konnte Sound-Ordner nicht anlegen", e);
        }
        config.save();
        VoiceBoard.LOGGER.info("VoiceBoard: Sounds und Einstellungen in {}", Storage.dataDir());
        return config;
    }

    /** Liest eine Einstellungsdatei; alte Formate werden dabei umgestellt. */
    private static BoardConfig read(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            boolean old = !json.has("keyFormat");
            BoardConfig config = GSON.fromJson(json, BoardConfig.class);
            if (config != null && old) {
                config.upgradeKeys();
            }
            return config;
        } catch (Exception e) {
            VoiceBoard.LOGGER.error("Konnte {} nicht lesen", path, e);
            return null;
        }
    }

    /** Alte Datei: ihre Tasten stammen vom Tastensystem der Version, in der sie lag (= die laufende). */
    private void upgradeKeys() {
        if (sounds != null) {
            for (SoundEntry s : sounds) {
                if (s != null && s.key != null) s.key.upgradeFromSingle(sdlKeys);
            }
        }
        if (stopAllKey != null) stopAllKey.upgradeFromSingle(sdlKeys);
        if (filterToggleKey != null) filterToggleKey.upgradeFromSingle(sdlKeys);
        keyFormat = 2;
    }

    private void sanitize() {
        if (sounds == null) sounds = new ArrayList<>();
        sounds.removeIf(s -> s == null || s.file == null);
        for (SoundEntry s : sounds) {
            if (s.name == null || s.name.isBlank()) s.name = stripExtension(s.file);
            if (s.key == null) s.key = new KeyBind();
            s.volume = Math.max(0, Math.min(MAX_VOLUME, s.volume));
        }
        if (stopAllKey == null) stopAllKey = new KeyBind();
        if (filterToggleKey == null) filterToggleKey = new KeyBind();
        if (filter == null) filter = FilterType.NONE;
        if (filterSettings == null) filterSettings = new FilterSettings();
        filterSettings.sanitize();
        masterVolume = Math.max(0, Math.min(MAX_VOLUME, masterVolume));
        monitorVolume = clamp(monitorVolume);
        filterStrength = clamp(filterStrength);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public static String stripExtension(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    /** Fügt eine Datei als Sound hinzu, falls sie noch nicht in der Liste ist. */
    public void addSound(String file) {
        if (!contains(file)) {
            sounds.add(new SoundEntry(file, stripExtension(file)));
        }
    }

    /** Der Filter, der gerade wirklich angewendet wird. */
    public FilterType activeFilter() {
        return filterEnabled ? filter : FilterType.NONE;
    }

    public SoundEntry find(String file) {
        for (SoundEntry s : sounds) {
            if (s.file.equals(file)) return s;
        }
        return null;
    }

    public boolean contains(String file) {
        for (SoundEntry s : sounds) {
            if (s.file.equals(file)) return true;
        }
        return false;
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        dirty = false;
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            VoiceBoard.LOGGER.error("Konnte {} nicht speichern", path, e);
        }
    }
}
