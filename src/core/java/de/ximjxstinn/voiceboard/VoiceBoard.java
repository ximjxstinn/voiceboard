package de.ximjxstinn.voiceboard;

import de.ximjxstinn.voiceboard.audio.LocalMonitor;
import de.ximjxstinn.voiceboard.audio.SoundPlayer;
import de.ximjxstinn.voiceboard.config.BoardConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gemeinsamer Kern – enthält KEINEN Minecraft-Code und läuft deshalb in jeder Version.
 * Alles, was Minecraft braucht, geht über {@link Platform}.
 */
public final class VoiceBoard {

    public static final String MOD_ID = "voiceboard";
    public static final Logger LOGGER = LoggerFactory.getLogger("VoiceBoard");

    private static BoardConfig config;
    private static final SoundPlayer PLAYER = new SoundPlayer();
    private static final LocalMonitor MONITOR = new LocalMonitor();
    private static volatile Platform platform;
    /** Von der Variante gesetzt, falls Mod Menu installiert ist (ein ConfigScreenFactory). */
    private static volatile Object modMenuFactory;

    private VoiceBoard() {
    }

    static void init(boolean sdlKeys) {
        BoardConfig.sdlKeys = sdlKeys;
        config = BoardConfig.load();
    }

    public static void setPlatform(Platform p) {
        platform = p;
    }

    public static void setModMenuFactory(Object factory) {
        modMenuFactory = factory;
    }

    public static Object modMenuFactory() {
        return modMenuFactory;
    }

    public static BoardConfig config() {
        return config;
    }

    public static SoundPlayer player() {
        return PLAYER;
    }

    public static LocalMonitor monitor() {
        return MONITOR;
    }

    /** Kurze Meldung über der Hotbar (falls ein Spiel läuft). */
    public static void notifyPlayer(String message) {
        Platform p = platform;
        if (p != null) {
            p.notifyPlayer(message);
        }
    }

    /** Führt Code im Minecraft-Hauptthread aus. */
    public static void runOnClient(Runnable task) {
        Platform p = platform;
        if (p != null) {
            p.runOnClient(task);
        } else {
            task.run();
        }
    }
}
