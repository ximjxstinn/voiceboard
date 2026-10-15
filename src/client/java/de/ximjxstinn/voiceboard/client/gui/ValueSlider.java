package de.ximjxstinn.voiceboard.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Schieberegler mit frei wählbarem Bereich und Einheit, z. B. "Verzögerung: 320 ms". */
public class ValueSlider extends AbstractSliderButton {

    private final String label;
    private final String unit;
    private final int min;
    private final int max;
    private final IntConsumer onChange;

    public ValueSlider(int x, int y, int width, int height, String label, String unit, int min, int max, int current, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), (current - min) / (double) (max - min));
        this.label = label;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        updateMessage();
    }

    private int current() {
        return (int) Math.round(min + value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + current() + " " + unit));
    }

    @Override
    protected void applyValue() {
        if (onChange != null) {
            onChange.accept(current());
        }
    }
}
