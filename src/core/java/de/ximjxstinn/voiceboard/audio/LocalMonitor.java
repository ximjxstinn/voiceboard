package de.ximjxstinn.voiceboard.audio;

import de.ximjxstinn.voiceboard.VoiceBoard;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Spielt die Soundboard-Sounds zusätzlich über deine eigenen Lautsprecher ab,
 * damit du hörst, was die anderen hören.
 */
public class LocalMonitor {

    private static final AudioFormat FORMAT = new AudioFormat(VoiceFilter.SAMPLE_RATE, 16, 1, true, false);

    private SourceDataLine line;
    private boolean failed;

    public synchronized void write(float[] frame, float volume) {
        if (failed) {
            return;
        }
        try {
            if (line == null) {
                line = AudioSystem.getSourceDataLine(FORMAT);
                line.open(FORMAT, VoiceFilter.SAMPLE_RATE / 5 * 2); // ~200 ms Puffer
                line.start();
            }
            byte[] bytes = new byte[frame.length * 2];
            for (int i = 0; i < frame.length; i++) {
                short s = VoiceFilter.toShort(de.ximjxstinn.voiceboard.VoiceBoardPlugin.softLimit(frame[i] * volume));
                bytes[i * 2] = (byte) s;
                bytes[i * 2 + 1] = (byte) (s >> 8);
            }
            // Nie blockieren: Ist der Puffer voll, wird der Frame lokal übersprungen.
            if (line.available() >= bytes.length) {
                line.write(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            failed = true;
            VoiceBoard.LOGGER.warn("Lokales Mithören nicht möglich", e);
        }
    }

    public synchronized void close() {
        if (line != null) {
            line.close();
            line = null;
        }
    }
}
