package de.ximjxstinn.voiceboard.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.IntConsumer;

/**
 * Schieberegler in Prozent. Standard 0–100 %, für Lautstärken auch 0–200 %.
 * Über 100 % wird die Zahl orange angezeigt.
 */
public class PercentSlider extends AbstractSliderButton {

    private final String label;
    private final int max;
    private final IntConsumer onChange;

    public PercentSlider(int x, int y, int width, int height, String label, int percent, IntConsumer onChange) {
        this(x, y, width, height, label, percent, 100, onChange);
    }

    public PercentSlider(int x, int y, int width, int height, String label, int percent, int max, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), Math.max(0, Math.min(max, percent)) / (double) max);
        this.label = label;
        this.max = max;
        this.onChange = onChange;
        updateMessage();
    }

    private int percent() {
        return (int) Math.round(value * max);
    }

    @Override
    protected void updateMessage() {
        String prefix = label == null || label.isEmpty() ? "" : label + ": ";
        MutableComponent text = Component.literal(prefix + percent() + " %");
        if (percent() > 100) {
            text = text.withStyle(ChatFormatting.GOLD); // verstärkt
        }
        setMessage(text);
    }

    @Override
    protected void applyValue() {
        if (onChange != null) {
            onChange.accept(percent());
        }
    }
}
