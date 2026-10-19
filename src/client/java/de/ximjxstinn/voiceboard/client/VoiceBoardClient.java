package de.ximjxstinn.voiceboard.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.ximjxstinn.voiceboard.Platform;
import de.ximjxstinn.voiceboard.VersionClient;
import de.ximjxstinn.voiceboard.VoiceBoard;
import de.ximjxstinn.voiceboard.audio.LocalMonitor;
import de.ximjxstinn.voiceboard.audio.SoundPlayer;
import de.ximjxstinn.voiceboard.client.compat.Compat;
import de.ximjxstinn.voiceboard.client.gui.BoardScreen;
import de.ximjxstinn.voiceboard.client.gui.PlayingHud;
import de.ximjxstinn.voiceboard.client.gui.SoundboardButton;
import de.ximjxstinn.voiceboard.config.BoardConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Client-Teil für EINE Minecraft-Version. Beim Bauen wird dieses Paket pro Version
 * kopiert (mc26_1, mc26_2, mc26_3); der Starter lädt nur die passende Kopie.
 */
public class VoiceBoardClient implements VersionClient {

    public static final String MOD_ID = VoiceBoard.MOD_ID;
    public static final Logger LOGGER = VoiceBoard.LOGGER;

    private static KeyMapping openKey;
    private final Map<BoardConfig.KeyBind, Boolean> wasDown = new IdentityHashMap<>();
    private int saveTimer;

    @Override
    public boolean sdlKeys() {
        return Compat.SDL_KEYS;
    }

    @Override
    public void init() {
        VoiceBoard.setPlatform(new Platform() {
            @Override
            public void notifyPlayer(String message) {
                VoiceBoardClient.notifyPlayer(message);
            }

            @Override
            public void runOnClient(Runnable task) {
                Minecraft.getInstance().execute(task);
            }
        });

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "voiceboard"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voiceboard.open", Compat.defaultOpenKey(), category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // "Konfigurieren"-Knopf in Mod Menu (nur wenn Mod Menu installiert ist)
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
            ModMenuHook.install();
        }

        // Anzeige "Sound läuft" unten links (über dem Mikrofon-Symbol von Simple Voice Chat)
        HudElementRegistry.attachElementBefore(VanillaHudElements.MOB_EFFECTS,
                Identifier.fromNamespaceAndPath(MOD_ID, "playing"), PlayingHud::render);

        // Soundboard-Knopf ins Voice-Chat-Menü von Simple Voice Chat (Taste V) einfügen
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!SVC_SCREEN.equals(screen.getClass().getName())) {
                return;
            }
            // Gleiche Maße wie im Menü von Simple Voice Chat (195 x 76), freier Platz rechts neben "Lautstärken"
            int guiLeft = (width - 195) / 2;
            int guiTop = (height - 76) / 2;
            Screens.getWidgets(screen).add(new SoundboardButton(guiLeft + 6 + 3 * 22, guiTop + 76 - 6 - 20));
        });
    }

    private static final String SVC_SCREEN = "de.maxhenkel.voicechat.gui.VoiceChatScreen";

    private void onTick(Minecraft mc) {
        BoardConfig config = config();
        while (openKey.consumeClick()) {
            if (Compat.screen(mc) == null) {
                Compat.setScreen(mc, new BoardScreen());
            }
        }

        // Hotkeys nur, wenn kein Menü/Chat offen ist (sonst würde Tippen Sounds auslösen)
        if (Compat.screen(mc) == null && mc.player != null) {
            if (pressed(config.stopAllKey)) {
                player().stopAll();
            }
            if (pressed(config.filterToggleKey)) {
                config.filterEnabled = !config.filterEnabled;
                config.save();
                mc.player.sendOverlayMessage(Component.literal("Voicefilter: " + (config.filterEnabled ? "§aAn" : "§cAus")));
            }
            for (BoardConfig.SoundEntry sound : config.sounds) {
                if (pressed(sound.key)) {
                    boolean wasPlaying = player().isPlaying(sound.file);
                    boolean wasPaused = player().isPaused(sound.file);
                    player().toggle(sound);
                    if (wasPlaying) {
                        mc.player.sendOverlayMessage(Component.literal("§eII " + sound.name + " pausiert"));
                    } else if (wasPaused) {
                        mc.player.sendOverlayMessage(Component.literal("§a▶ " + sound.name));
                    }
                }
            }
        } else {
            wasDown.clear();
        }

        if (++saveTimer >= 40) {
            saveTimer = 0;
            config.saveIfDirty();
        }
    }

    /** True genau in dem Tick, in dem die Taste heruntergedrückt wird. */
    private boolean pressed(BoardConfig.KeyBind bind) {
        if (!bind.isBound() || "MOUSE".equals(bind.type())) {
            return false;
        }
        boolean down = Compat.isKeyDown(bind.code());
        Boolean before = wasDown.put(bind, down);
        return down && (before == null || !before);
    }

    /** Anzeigename einer Tastenbelegung, z. B. "F13". */
    public static Component keyName(BoardConfig.KeyBind bind) {
        if (!bind.isBound()) {
            return Component.literal("Nicht belegt");
        }
        try {
            return InputConstants.Type.valueOf(bind.type()).getOrCreate(bind.code()).getDisplayName();
        } catch (Exception e) {
            return Component.literal("Taste " + bind.code());
        }
    }

    public static void notifyPlayer(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal(message));
            }
        });
    }

    public static BoardConfig config() {
        return VoiceBoard.config();
    }

    public static SoundPlayer player() {
        return VoiceBoard.player();
    }

    public static LocalMonitor monitor() {
        return VoiceBoard.monitor();
    }
}
