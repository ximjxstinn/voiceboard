package de.ximjxstinn.voiceboard;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Verbindung zu Mod Menu: Der "Konfigurieren"-Knopf öffnet das VoiceBoard-Menü.
 * Wird nur geladen, wenn Mod Menu installiert ist. Das eigentliche Menü liefert
 * die zur Minecraft-Version passende Variante.
 */
public class VoiceBoardModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (ConfigScreenFactory<?>) VoiceBoard.modMenuFactory();
    }
}
