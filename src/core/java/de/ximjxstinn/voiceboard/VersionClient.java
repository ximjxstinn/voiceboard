package de.ximjxstinn.voiceboard;

/** Einstieg einer versionsspezifischen Client-Variante. */
public interface VersionClient {

    /** Ob diese Minecraft-Version das neue Tastensystem (SDL) nutzt. */
    boolean sdlKeys();

    void init();
}
