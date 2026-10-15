package de.ximjxstinn.voiceboard.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Deutlich sichtbarer Loop-Schalter: grün = an, dunkelgrau = aus. */
public class LoopButton extends AbstractButton {

    private static final Component LABEL = Component.literal("↻ Loop");

    private boolean on;
    private final Consumer<Boolean> onToggle;

    public LoopButton(int x, int y, int width, int height, boolean on, Consumer<Boolean> onToggle) {
        super(x, y, width, height, LABEL);
        this.on = on;
        this.onToggle = onToggle;
        updateTooltip();
    }

    private void updateTooltip() {
        setTooltip(Tooltip.create(Component.literal(on
                ? "Loop an – der Sound wiederholt sich, bis du ihn stoppst"
                : "Loop aus – der Sound läuft einmal")));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        updateTooltip();
        onToggle.accept(on);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;

        int bg, top, bottom, text;
        if (on) {
            bg = hover ? 0xFF3AA84C : 0xFF2E8C3C;
            top = 0xFF6ED278;
            bottom = 0xFF1E5A28;
            text = 0xFFFFFFFF;
        } else {
            bg = hover ? 0xFF5A5A5A : 0xFF464646;
            top = 0xFF6E6E6E;
            bottom = 0xFF323232;
            text = 0xFF8C8C8C;
        }
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, top);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottom);
        g.centeredText(Minecraft.getInstance().font, LABEL, x + w / 2, y + (h - 8) / 2, text);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
