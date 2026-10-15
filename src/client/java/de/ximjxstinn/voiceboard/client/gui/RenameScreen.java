package de.ximjxstinn.voiceboard.client.gui;

import de.ximjxstinn.voiceboard.client.compat.Compat;
import de.ximjxstinn.voiceboard.client.VoiceBoardClient;
import de.ximjxstinn.voiceboard.config.BoardConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Kleiner Dialog zum Umbenennen eines Sounds (Stift-Button). */
public class RenameScreen extends Screen {

    private final Screen parent;
    private final BoardConfig.SoundEntry entry;
    private EditBox nameBox;

    public RenameScreen(Screen parent, BoardConfig.SoundEntry entry) {
        super(Component.literal("Sound umbenennen"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int w = Math.min(260, width - 20);
        int x = (width - w) / 2;
        int y = height / 2 - 30;

        nameBox = new EditBox(font, x, y, w, 20, Component.literal("Name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(entry.name);
        addRenderableWidget(nameBox);
        setFocused(nameBox);

        int bw = (w - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Speichern"), b -> save())
                .bounds(x, y + 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> back())
                .bounds(x + bw + 4, y + 28, bw, 20).build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        entry.name = name.isEmpty() ? BoardConfig.stripExtension(entry.file) : name;
        VoiceBoardClient.config().save();
        back();
    }

    private void back() {
        Compat.setScreen(minecraft, parent);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.isEscape()) {
            back();
            return true;
        }
        if (keyEvent.isConfirmation()) {
            save();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, height / 2 - 48, -1);
        g.centeredText(font, Component.literal("Datei: " + entry.file), width / 2, height / 2 + 26 + 28, 0xFFA0A0A0);
    }
}
