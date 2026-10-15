package de.ximjxstinn.voiceboard.client.gui;

import de.ximjxstinn.voiceboard.client.VoiceBoardClient;
import de.ximjxstinn.voiceboard.audio.FilterType;
import de.ximjxstinn.voiceboard.config.BoardConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Symbole unten links neben dem Mikrofon von Simple Voice Chat:
 * Musiknote, solange ein Sound läuft, und Schallwelle, solange ein Voicefilter aktiv ist.
 */
public final class PlayingHud {

    private static final Identifier NOTE = Identifier.fromNamespaceAndPath("voiceboard", "icons/soundboard_button");
    private static final Identifier FILTER = Identifier.fromNamespaceAndPath("voiceboard", "icons/voicefilter_active");

    private PlayingHud() {
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        BoardConfig config = VoiceBoardClient.config();
        if (config == null || !config.showHud) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean playing = VoiceBoardClient.player().hasActive();
        boolean filtering = config.activeFilter() != FilterType.NONE && config.filterStrength > 0;
        if (!playing && !filtering) {
            return;
        }
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Rechts neben dem Mikrofon-Symbol von Simple Voice Chat (gleiche Höhe: 16 px über dem unteren Rand)
        int x = 34;
        int y = screenH - 32;
        if (playing) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, NOTE, x, y, 16, 16);
            x += 18;
        }
        if (filtering) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FILTER, x, y, 16, 16);
        }
    }

}
