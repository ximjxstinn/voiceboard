package de.ximjxstinn.voiceboard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Starter der Mod. Enthält keinen Minecraft-Code: Er liest nur die Minecraft-Version
 * und lädt dann die passende Variante (mc26_1, mc26_2 oder mc26_3).
 * Die Klassen der anderen Varianten werden nie geladen.
 */
public class VoiceBoardEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        String variant = variantFor(mc);
        VoiceBoard.LOGGER.info("VoiceBoard: Minecraft {} erkannt, lade Variante {}", mc, variant);

        VersionClient client;
        try {
            Class<?> cls = Class.forName("de.ximjxstinn.voiceboard." + variant + ".VoiceBoardClient");
            client = (VersionClient) cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("VoiceBoard: Variante " + variant + " fehlt in der Jar", e);
        }
        VoiceBoard.init(client.sdlKeys());
        client.init();
    }

    static String variantFor(String mc) {
        if (mc.equals("26.1") || mc.startsWith("26.1.") || mc.startsWith("26.1-")) return "mc26_1";
        if (mc.equals("26.2") || mc.startsWith("26.2.") || mc.startsWith("26.2-")) return "mc26_2";
        return "mc26_3";
    }
}
