package de.ximjxstinn.voiceboard.audio;

import de.ximjxstinn.voiceboard.VoiceBoard;
import de.ximjxstinn.voiceboard.config.BoardConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hält die geladenen Sounds im Speicher und mischt die gerade laufenden Sounds
 * Frame für Frame zusammen.
 */
public class SoundPlayer {

    private final Map<String, short[]> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final List<Playback> active = new ArrayList<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VoiceBoard-Loader");
        t.setDaemon(true);
        return t;
    });

    private static class Playback {
        final BoardConfig.SoundEntry entry;
        final short[] samples;
        int position;

        Playback(BoardConfig.SoundEntry entry, short[] samples) {
            this.entry = entry;
            this.samples = samples;
        }
    }

    /** Startet den Sound, oder stoppt ihn, falls er schon läuft. */
    public void toggle(BoardConfig.SoundEntry entry) {
        if (isPlaying(entry.file)) {
            stop(entry.file);
        } else {
            play(entry);
        }
    }

    public void play(BoardConfig.SoundEntry entry) {
        short[] samples = cache.get(entry.file);
        if (samples != null) {
            start(entry, samples);
            return;
        }
        if (!loading.add(entry.file)) {
            return;
        }
        Path path = BoardConfig.soundFolder().resolve(entry.file);
        loader.execute(() -> {
            try {
                if (!Files.exists(path)) {
                    VoiceBoard.notifyPlayer("Datei nicht gefunden: " + entry.file);
                    return;
                }
                short[] decoded = AudioLoader.load(path);
                cache.put(entry.file, decoded);
                start(entry, decoded);
            } catch (Exception e) {
                VoiceBoard.LOGGER.error("Konnte {} nicht laden", path, e);
                VoiceBoard.notifyPlayer("Konnte " + entry.file + " nicht laden");
            } finally {
                loading.remove(entry.file);
            }
        });
    }

    private synchronized void start(BoardConfig.SoundEntry entry, short[] samples) {
        active.removeIf(p -> p.entry.file.equals(entry.file));
        active.add(new Playback(entry, samples));
    }

    public synchronized void stop(String file) {
        active.removeIf(p -> p.entry.file.equals(file));
    }

    public synchronized void stopAll() {
        active.clear();
    }

    public synchronized boolean isPlaying(String file) {
        for (Playback p : active) {
            if (p.entry.file.equals(file)) return true;
        }
        return false;
    }

    public boolean isLoading(String file) {
        return loading.contains(file);
    }

    public synchronized boolean hasActive() {
        return !active.isEmpty();
    }

    /** Namen der gerade laufenden Sounds (zuletzt gestarteter zuerst). */
    public synchronized List<String> activeNames() {
        List<String> names = new ArrayList<>();
        for (int i = active.size() - 1; i >= 0; i--) {
            names.add(active.get(i).entry.name);
        }
        return names;
    }

    /** Ob mindestens einer der laufenden Sounds im Loop-Modus ist. */
    public synchronized boolean anyLooping() {
        for (Playback p : active) {
            if (p.entry.loop) return true;
        }
        return false;
    }

    /** Fortschritt eines laufenden Sounds (0..1) oder -1. */
    public synchronized float progress(String file) {
        for (Playback p : active) {
            if (p.entry.file.equals(file)) return (float) p.position / Math.max(1, p.samples.length);
        }
        return -1F;
    }

    /** Vergisst zwischengespeicherte Audiodaten, z. B. nach "Neu laden". */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Mischt den nächsten Frame aller laufenden Sounds (ohne Master-Lautstärke).
     *
     * @return Samples im Bereich -1..1 oder null, wenn nichts läuft
     */
    public synchronized float[] nextFrame(int length) {
        if (active.isEmpty()) {
            return null;
        }
        float[] mix = new float[length];
        Iterator<Playback> it = active.iterator();
        while (it.hasNext()) {
            Playback p = it.next();
            float volume = p.entry.volume / 100F;
            int filled = 0;
            while (filled < length) {
                int n = Math.min(length - filled, p.samples.length - p.position);
                for (int i = 0; i < n; i++) {
                    mix[filled + i] += p.samples[p.position + i] / 32768F * volume;
                }
                p.position += n;
                filled += n;
                if (p.position >= p.samples.length) {
                    if (p.entry.loop && p.samples.length > 0) {
                        p.position = 0; // Loop: von vorne
                    } else {
                        it.remove();
                        break;
                    }
                }
            }
        }
        return mix;
    }
}
