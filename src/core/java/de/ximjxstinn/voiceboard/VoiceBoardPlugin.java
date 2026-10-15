package de.ximjxstinn.voiceboard;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.ximjxstinn.voiceboard.audio.VoiceFilter;
import de.ximjxstinn.voiceboard.config.BoardConfig;

/**
 * Verbindung zu Simple Voice Chat.
 * <p>
 * Ablauf pro 20-ms-Frame im Mikrofon-Thread von Simple Voice Chat:
 * <ol>
 *     <li>{@link MergeClientSoundEvent}: Läuft ein Sound, hängen wir einen stillen Frame an.
 *     Das sorgt dafür, dass auch gesendet wird, wenn du gerade nicht sprichst oder stumm bist.</li>
 *     <li>{@link ClientSoundEvent}: Zuerst bekommt nur deine Stimme den Voicefilter,
 *     danach mischen wir das Soundboard dazu. So werden die Sounds nicht mitverzerrt.</li>
 * </ol>
 */
public class VoiceBoardPlugin implements VoicechatPlugin {

    /** 20 ms bei 48 kHz. */
    private static final int FRAME_SIZE = 960;

    private final VoiceFilter filter = new VoiceFilter();

    @Override
    public String getPluginId() {
        return "voiceboard";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, this::onMerge);
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
    }

    private void onMerge(MergeClientSoundEvent event) {
        BoardConfig config = VoiceBoard.config();
        if (config == null || !VoiceBoard.player().hasActive()) {
            return;
        }
        if (!config.playWhileMuted && event.getVoicechat().isMuted()) {
            return;
        }
        event.mergeAudio(new short[FRAME_SIZE]);
    }

    private void onClientSound(ClientSoundEvent event) {
        BoardConfig config = VoiceBoard.config();
        if (config == null) {
            return;
        }
        short[] audio = event.getRawAudio();

        // 1. Voicefilter nur auf die Stimme
        filter.process(audio, config.activeFilter(), config.filterStrength / 100F, config.filterSettings);

        // 2. Soundboard dazumischen
        float[] board = VoiceBoard.player().nextFrame(audio.length);
        if (board != null) {
            float master = config.masterVolume / 100F;
            for (int i = 0; i < audio.length; i++) {
                float mixed = audio[i] / 32768F + softLimit(board[i] * master);
                audio[i] = toShort(mixed);
            }
            if (config.hearSelf) {
                VoiceBoard.monitor().write(board, master * config.monitorVolume / 100F);
            }
        }
        event.setRawAudio(audio);
    }

    /**
     * Sanfter Begrenzer: Bis 80 % Aussteuerung unverändert, darüber wird weich abgerundet
     * statt hart abgeschnitten. So klingen Sounds über 100 % lauter, aber nicht kratzig.
     */
    public static float softLimit(float x) {
        float a = Math.abs(x);
        if (a <= 0.8F) {
            return x;
        }
        float limited = 0.8F + 0.2F * (float) Math.tanh((a - 0.8F) / 0.2F);
        return Math.copySign(limited, x);
    }

    private static short toShort(float v) {
        if (v > 1F) v = 1F;
        if (v < -1F) v = -1F;
        return (short) Math.round(v * 32767F);
    }
}
