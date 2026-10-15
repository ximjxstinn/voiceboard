package com.terraformersmc.modmenu.api;

/**
 * Nur zum Kompilieren: Nachbau der Schnittstelle von Mod Menu.
 * Wird NICHT in die Jar gepackt – zur Laufzeit kommt die echte Klasse aus Mod Menu.
 */
public interface ModMenuApi {

    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
