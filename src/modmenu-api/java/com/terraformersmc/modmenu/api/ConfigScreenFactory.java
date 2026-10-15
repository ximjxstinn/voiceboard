package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Nur zum Kompilieren (siehe ModMenuApi). Wird NICHT in die Jar gepackt. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {

    S create(Screen parent);
}
