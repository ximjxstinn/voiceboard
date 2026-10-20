package de.ximjxstinn.voiceboard.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Versionsabhängige Stellen – Minecraft 26.1.x. */
public final class Compat {

    /** Neues Tastensystem (SDL) ab 26.3. */
    public static final boolean SDL_KEYS = false;

    public static final String MINECRAFT = "26.1.x";

    private Compat() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    /** Standardtaste zum Öffnen des Menüs (J). */
    public static int defaultOpenKey() {
        return GLFW.GLFW_KEY_J;
    }

    public static boolean isKeyDown(int code) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
    }

    /** Kurze Meldung über der Hotbar. */
    public static void overlay(LocalPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }

    public static KeyMapping registerKey(KeyMapping key) {
        return KeyMappingHelper.registerKeyMapping(key);
    }

    /** HUD-Ebene, vor der die VoiceBoard-Symbole gezeichnet werden. */
    public static final Identifier HUD_ANCHOR = VanillaHudElements.MOB_EFFECTS;
}
