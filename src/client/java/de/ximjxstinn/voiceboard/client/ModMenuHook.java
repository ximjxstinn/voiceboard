package de.ximjxstinn.voiceboard.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import de.ximjxstinn.voiceboard.VoiceBoard;
import de.ximjxstinn.voiceboard.client.gui.BoardScreen;

/**
 * Eigene Klasse, damit Mod-Menu-Klassen nur angefasst werden, wenn Mod Menu installiert ist.
 */
final class ModMenuHook {

    private ModMenuHook() {
    }

    static void install() {
        ConfigScreenFactory<BoardScreen> factory = BoardScreen::new;
        VoiceBoard.setModMenuFactory(factory);
    }
}
