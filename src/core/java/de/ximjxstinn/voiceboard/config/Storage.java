package de.ximjxstinn.voiceboard.config;

import de.ximjxstinn.voiceboard.VoiceBoard;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Gemeinsamer Speicherort für ALLE Minecraft-Versionen und Launcher-Profile.
 * <ul>
 *     <li>Windows: %APPDATA%\VoiceBoard</li>
 *     <li>macOS: ~/Library/Application Support/VoiceBoard</li>
 *     <li>Linux: ~/.config/voiceboard</li>
 * </ul>
 * Darin liegen voiceboard.json, sounds/ und tools/.
 */
public final class Storage {

    private static Path dataDir;

    private Storage() {
    }

    public static synchronized Path dataDir() {
        if (dataDir == null) {
            Path shared = sharedDir();
            try {
                Files.createDirectories(shared);
                dataDir = shared;
            } catch (Exception e) {
                // Notfalls wie früher im Minecraft-Ordner speichern
                VoiceBoard.LOGGER.error("VoiceBoard: gemeinsamer Ordner {} nicht nutzbar, speichere im Minecraft-Ordner", shared, e);
                dataDir = FabricLoader.getInstance().getGameDir().resolve("voiceboard");
            }
        }
        return dataDir;
    }

    public static Path configFile() {
        return dataDir().resolve("voiceboard.json");
    }

    public static Path soundFolder() {
        return dataDir().resolve("sounds");
    }

    public static Path toolsFolder() {
        return dataDir().resolve("tools");
    }

    private static Path sharedDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", "."));
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null && !appData.isBlank() ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
            return base.resolve("VoiceBoard");
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("VoiceBoard");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".config");
        return base.resolve("voiceboard");
    }

    // ------------------------------------------------------------ Übernahme

    /** Alte Einstellungen (ältere Versionen, im Minecraft-Profil). */
    static Path legacyConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("voiceboard.json");
    }

    /**
     * Kopiert einmalig pro Minecraft-Profil die alten Sounds in den gemeinsamen Ordner.
     * Die alten Dateien bleiben unangetastet.
     */
    static boolean shouldImportLegacy() {
        Path marker = FabricLoader.getInstance().getGameDir().resolve("voiceboard").resolve(".in-gemeinsamen-ordner-uebernommen");
        if (Files.exists(marker) || dataDir().equals(FabricLoader.getInstance().getGameDir().resolve("voiceboard"))) {
            return false;
        }
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Sounds und Einstellungen liegen jetzt in: " + dataDir() + System.lineSeparator());
        } catch (Exception e) {
            VoiceBoard.LOGGER.warn("VoiceBoard: Markierung nicht schreibbar", e);
        }
        return true;
    }

    static void copyLegacySounds() {
        Path old = FabricLoader.getInstance().getGameDir().resolve("voiceboard").resolve("sounds");
        if (!Files.isDirectory(old)) {
            return;
        }
        try {
            Files.createDirectories(soundFolder());
            try (Stream<Path> files = Files.list(old)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    Path target = soundFolder().resolve(f.getFileName().toString());
                    if (Files.isRegularFile(f) && !Files.exists(target)) {
                        Files.copy(f, target, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            VoiceBoard.LOGGER.info("VoiceBoard: Sounds aus {} übernommen", old);
        } catch (Exception e) {
            VoiceBoard.LOGGER.error("VoiceBoard: Sounds aus {} konnten nicht übernommen werden", old, e);
        }
    }
}
