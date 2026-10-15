package de.ximjxstinn.voiceboard.client.gui;

import de.ximjxstinn.voiceboard.client.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Knopf mit Musiknoten-Icon im Simple-Voice-Chat-Menü, öffnet das Soundboard. */
public class SoundboardButton extends AbstractButton {

    /** Liegt in assets/voiceboard/textures/icons/ und landet so im GUI-Atlas (wie bei Simple Voice Chat). */
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("voiceboard", "icons/soundboard_button");

    public SoundboardButton(int x, int y) {
        super(x, y, 20, 20, Component.literal("Soundboard"));
        setTooltip(Tooltip.create(Component.literal("Soundboard & Voicefilter")));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        Compat.setScreen(Minecraft.getInstance(), new BoardScreen());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractDefaultSprite(graphics);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON, getX() + 2, getY() + 2, 16, 16);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
