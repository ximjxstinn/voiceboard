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
 * Frame für Frame zusammen. Pausierte Sounds behalten ihre Stelle.
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
        boolean paused;

        Playback(BoardConfig.SoundEntry entry, short[] samples) {
            this.entry = entry;
            this.samples = samples;
        }
    }

    /** Infos zum Sound, den der Mini-Player gerade anzeigt. */
    public record NowPlaying(String file, String name, int position, int length, boolean paused) {
        public float progress() {
            return (float) position / Math.max(1, length);
        }
    }

    /**
     * Startet den Sound. Läuft er schon, wird er pausiert; ist er pausiert,
     * läuft er an derselben Stelle weiter.
     */
    public void toggle(BoardConfig.SoundEntry entry) {
        Boolean paused = pausedState(entry.file);
        if (paused == null) {
            play(entry);
        } else {
            setPaused(entry.file, !paused);
        }
    }

    /** null = nicht geladen/aktiv, sonst ob pausiert. */
    private synchronized Boolean pausedState(String file) {
        Playback p = find(file);
        return p == null ? null : p.paused;
    }

    private Playback find(String file) {
        for (Playback p : active) {
            if (p.entry.file.equals(file)) return p;
        }
        return null;
    }

    /** Pausiert oder setzt fort, ohne die Stelle zu verlieren. */
    public synchronized void setPaused(String file, boolean paused) {
        Playback p = find(file);
        if (p != null) {
            p.paused = paused;
            focus(p);
        }
    }

    /** Springt um die angegebenen Sekunden vor (positiv) oder zurück (negativ). */
    public synchronized void skip(String file, int seconds) {
        Playback p = find(file);
        if (p != null) {
            moveTo(p, p.position + seconds * VoiceFilter.SAMPLE_RATE);
        }
    }

    /** Springt an eine Stelle (0 = Anfang, 1 = Ende). */
    public synchronized void seek(String file, double fraction) {
        Playback p = find(file);
        if (p != null) {
            moveTo(p, (int) Math.round(Math.max(0D, Math.min(1D, fraction)) * p.samples.length));
        }
    }

    private void moveTo(Playback p, int position) {
        // Kurz vor dem Ende anhalten, damit Vorspulen den Sound nicht sofort beendet
        int last = Math.max(0, p.samples.length - 1);
        p.position = Math.max(0, Math.min(last, position));
        focus(p);
    }

    /** Zuletzt benutzter Sound rückt nach hinten = wird im Mini-Player angezeigt. */
    private void focus(Playback p) {
        if (active.remove(p)) {
            active.add(p);
        }
    }

    /** Der zuletzt gestartete oder bediente Sound (laufend oder pausiert), oder null. */
    public synchronized NowPlaying current() {
        if (active.isEmpty()) {
            return null;
        }
        Playback p = active.get(active.size() - 1);
        return new NowPlaying(p.entry.file, p.entry.name, p.position, p.samples.length, p.paused);
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

    /** Läuft gerade hörbar (nicht pausiert). */
    public synchronized boolean isPlaying(String file) {
        Playback p = find(file);
        return p != null && !p.paused;
    }

    public synchronized boolean isPaused(String file) {
        Playback p = find(file);
        return p != null && p.paused;
    }

    public boolean isLoading(String file) {
        return loading.contains(file);
    }

    /** Ob gerade mindestens ein Sound hörbar läuft (pausierte zählen nicht). */
    public synchronized boolean hasActive() {
        for (Playback p : active) {
            if (!p.paused) return true;
        }
        return false;
    }

    /** Namen der gerade laufenden Sounds (zuletzt gestarteter zuerst). */
    public synchronized List<String> activeNames() {
        List<String> names = new ArrayList<>();
        for (int i = active.size() - 1; i >= 0; i--) {
            if (active.get(i).paused) continue;
            names.add(active.get(i).entry.name);
        }
        return names;
    }

    /** Ob mindestens einer der laufenden Sounds im Loop-Modus ist. */
    public synchronized boolean anyLooping() {
        for (Playback p : active) {
            if (p.entry.loop && !p.paused) return true;
        }
        return false;
    }

    /** Fortschritt eines laufenden oder pausierten Sounds (0..1) oder -1. */
    public synchronized float progress(String file) {
        Playback p = find(file);
        return p == null ? -1F : (float) p.position / Math.max(1, p.samples.length);
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
        if (!hasActive()) {
            return null;
        }
        float[] mix = new float[length];
        Iterator<Playback> it = active.iterator();
        while (it.hasNext()) {
            Playback p = it.next();
            if (p.paused) {
                continue; // bleibt an seiner Stelle stehen
            }
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
