package de.ximjxstinn.voiceboard.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.sdl.SDLScancode;

/** Versionsabhängige Stellen – Minecraft 26.3. */
public final class Compat {

    /** Neues Tastensystem (SDL) ab 26.3. */
    public static final boolean SDL_KEYS = true;

    public static final String MINECRAFT = "26.3";

    private Compat() {
    }

    public static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    /** Standardtaste zum Öffnen des Menüs (J). */
    public static int defaultOpenKey() {
        return SDLScancode.SDL_SCANCODE_J;
    }

    public static boolean isKeyDown(int code) {
        return InputConstants.isKeyDown(code);
    }
}
