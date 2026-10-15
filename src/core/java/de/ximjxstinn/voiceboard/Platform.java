package de.ximjxstinn.voiceboard;

/** Die Minecraft-Funktionen, die der Kern braucht – umgesetzt pro Minecraft-Version. */
public interface Platform {

    void notifyPlayer(String message);

    void runOnClient(Runnable task);
}
