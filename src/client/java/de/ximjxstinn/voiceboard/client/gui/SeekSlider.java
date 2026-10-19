package de.ximjxstinn.voiceboard.client.gui;

import de.ximjxstinn.voiceboard.audio.SoundPlayer;
import de.ximjxstinn.voiceboard.audio.VoiceFilter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.BiFunction;

/**
 * Spulregler des Mini-Players: zeigt Name und Zeit des zuletzt benutzten Sounds.
 * Ziehen oder Klicken springt an diese Stelle.
 */
public class SeekSlider extends AbstractSliderButton {

    private final SoundPlayer player;
    /** Kürzt Text auf eine Breite (kommt vom Menü, das die Schrift kennt). */
    private final BiFunction<String, Integer, String> fit;
    private final int textWidth;
    private String file;

    public SeekSlider(int x, int y, int width, int height, SoundPlayer player, BiFunction<String, Integer, String> fit) {
        super(x, y, width, height, Component.empty(), 0D);
        this.player = player;
        this.fit = fit;
        this.textWidth = width - 8;
        sync();
    }

    /** Jeden Frame aufrufen: übernimmt Stelle, Name und Zeit vom Player. */
    public void sync() {
        SoundPlayer.NowPlaying now = player.current();
        if (now == null) {
            file = null;
            active = false;
            value = 0D;
            setMessage(Component.literal("Kein Sound aktiv").withStyle(ChatFormatting.GRAY));
            return;
        }
        file = now.file();
        active = true;
        value = now.progress();
        String time = time(now.position()) + " / " + time(now.length());
        String name = fit.apply(now.name(), textWidth - 70);
        ChatFormatting color = now.paused() ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
        setMessage(Component.literal(name + "  " + time).withStyle(color));
    }

    private static String time(int samples) {
        int seconds = samples / VoiceFilter.SAMPLE_RATE;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    protected void updateMessage() {
        // Text kommt aus sync()
    }

    @Override
    protected void applyValue() {
        if (file != null) {
            player.seek(file, value);
        }
    }
}
