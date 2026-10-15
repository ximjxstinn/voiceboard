package de.ximjxstinn.voiceboard.audio;

import java.util.Random;

/**
 * Echtzeit-Voicefilter für 48 kHz Mono. Wird im Mikrofon-Thread von Simple Voice Chat
 * mit jeweils einem 20-ms-Frame aufgerufen und hält seinen Zustand zwischen den Frames.
 */
public class VoiceFilter {

    public static final int SAMPLE_RATE = 48000;

    private FilterType current = FilterType.NONE;

    // Pitch
    private final PitchShifter pitch = new PitchShifter();
    // Baby: leichtes Zittern der Tonhöhe
    private double babyPhase;
    // Roboter
    private double robotPhase;
    private final Comb robotComb = new Comb(ms(4), 0.55, 0.0);
    // Echo (Puffer für bis zu 1 s Verzögerung)
    private final float[] echoBuf = new float[ms(1000) + 1];
    private int echoPos;
    // Hall (Schroeder)
    private final Comb[] hallCombs = {
            new Comb(scale(1557), 0.84, 0.2), new Comb(scale(1617), 0.84, 0.2),
            new Comb(scale(1491), 0.84, 0.2), new Comb(scale(1422), 0.84, 0.2)
    };
    private final AllPass[] hallAllPass = {new AllPass(scale(225), 0.5), new AllPass(scale(556), 0.5)};
    // Radio / Telefon
    private final Biquad hp1 = new Biquad(), hp2 = new Biquad(), lp1 = new Biquad(), lp2 = new Biquad();
    private final Random random = new Random();

    /** Aktuelle Einstellungen, werden pro Frame aus der Config übernommen. */
    private FilterSettings settings = new FilterSettings();

    /**
     * Wendet den Filter auf die Audiodaten an (in place).
     *
     * @param strength Mischverhältnis 0..1 (0 = Originalstimme, 1 = voller Effekt)
     */
    public synchronized void process(short[] audio, FilterType type, float strength, FilterSettings settings) {
        if (settings != null) {
            this.settings = settings;
        }
        if (type != current) {
            reset(type);
        }
        if (type == FilterType.NONE || strength <= 0F) {
            return;
        }
        applySettings(type);
        for (int i = 0; i < audio.length; i++) {
            float dry = audio[i] / 32768F;
            float wet = apply(type, dry);
            float out = dry * (1F - strength) + wet * strength;
            audio[i] = toShort(out);
        }
    }

    /** Übernimmt geänderte Regler sofort, ohne den Filter neu zu starten. */
    private void applySettings(FilterType type) {
        FilterSettings s = settings;
        if (type == FilterType.PITCH_HIGH) pitch.setRatio(s.pitchHigh / 100.0);
        if (type == FilterType.PITCH_LOW) pitch.setRatio(s.pitchLow / 100.0);
        if (type == FilterType.HALL) {
            float fb = 0.70F + 0.25F * s.hallSize / 100F;
            for (Comb c : hallCombs) c.feedback = fb;
        }
    }

    private float apply(FilterType type, float x) {
        FilterSettings s = settings;
        switch (type) {
            case PITCH_HIGH:
            case PITCH_LOW:
                return pitch.process(x);
            case BABY: {
                // hohe Stimme mit kindlichem Zittern (ca. 5,5 Hz, +-3 %)
                babyPhase += 2 * Math.PI * 5.5 / SAMPLE_RATE;
                if (babyPhase > 2 * Math.PI) babyPhase -= 2 * Math.PI;
                pitch.setRatio(s.babyPitch / 100.0 * (1.0 + 0.03 * Math.sin(babyPhase)));
                return softClip(pitch.process(x) * 1.15F);
            }
            case ROBOT: {
                robotPhase += 2 * Math.PI * s.robotFrequency / SAMPLE_RATE;
                if (robotPhase > 2 * Math.PI) robotPhase -= 2 * Math.PI;
                float ring = (float) (x * Math.sin(robotPhase));
                return softClip(robotComb.process(ring) * 1.6F);
            }
            case ECHO: {
                int delay = Math.max(1, Math.min(echoBuf.length - 1, ms(s.echoDelay)));
                int readPos = (echoPos - delay + echoBuf.length) % echoBuf.length;
                float delayed = echoBuf[readPos];
                echoBuf[echoPos] = x + delayed * (s.echoFeedback / 100F);
                echoPos = (echoPos + 1) % echoBuf.length;
                return x + delayed * 0.7F;
            }
            case HALL: {
                float sum = 0F;
                for (Comb c : hallCombs) sum += c.process(x);
                float y = sum * 0.25F;
                for (AllPass a : hallAllPass) y = a.process(y);
                return x * 0.7F + y * 0.45F;
            }
            case RADIO: {
                float y = lp2.process(lp1.process(hp2.process(hp1.process(x))));
                float drive = 1F + 9F * s.radioDistortion / 100F;
                y = softClip(y * drive) * 0.8F;
                return y + (random.nextFloat() - 0.5F) * 0.012F;
            }
            case PHONE: {
                float y = lp2.process(lp1.process(hp2.process(hp1.process(x))));
                // Qualität 100 % = 12 Bit, 0 % = 4 Bit
                int bits = 4 + Math.round(8F * s.phoneQuality / 100F);
                float steps = (float) (1 << (bits - 1));
                y = Math.round(y * steps) / steps;
                return softClip(y * 2F) * 0.75F;
            }
            default:
                return x;
        }
    }

    private void reset(FilterType type) {
        current = type;
        if (type == FilterType.PITCH_HIGH) pitch.reset(settings.pitchHigh / 100.0);
        if (type == FilterType.PITCH_LOW) pitch.reset(settings.pitchLow / 100.0);
        if (type == FilterType.BABY) pitch.reset(settings.babyPitch / 100.0);
        babyPhase = 0;
        robotPhase = 0;
        robotComb.clear();
        java.util.Arrays.fill(echoBuf, 0F);
        echoPos = 0;
        for (Comb c : hallCombs) c.clear();
        for (AllPass a : hallAllPass) a.clear();
        if (type == FilterType.RADIO) {
            hp1.highPass(450); hp2.highPass(450);
            lp1.lowPass(2800); lp2.lowPass(2800);
        } else if (type == FilterType.PHONE) {
            hp1.highPass(320); hp2.highPass(320);
            lp1.lowPass(3300); lp2.lowPass(3300);
        }
    }

    private static int ms(double ms) {
        return (int) (SAMPLE_RATE * ms / 1000.0);
    }

    private static int scale(int samplesAt44k) {
        return (int) (samplesAt44k * SAMPLE_RATE / 44100.0);
    }

    static float softClip(float x) {
        return (float) Math.tanh(x);
    }

    static short toShort(float v) {
        int s = Math.round(v * 32767F);
        if (s > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (s < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) s;
    }

    /** Pitch-Shifter mit zwei überblendeten Lese-Zeigern auf einer Verzögerungsleitung. */
    private static class PitchShifter {
        private static final int WINDOW = 2048;
        private final float[] buf = new float[WINDOW * 2];
        private int write;
        private double phase;
        private double ratio = 1.0;

        void setRatio(double ratio) {
            this.ratio = ratio;
        }

        void reset(double ratio) {
            this.ratio = ratio;
            java.util.Arrays.fill(buf, 0F);
            write = 0;
            phase = 0;
        }

        float process(float x) {
            buf[write] = x;
            phase += (1.0 - ratio) / WINDOW;
            phase -= Math.floor(phase);
            double phase2 = (phase + 0.5) % 1.0;
            float y = read(phase * WINDOW) * window(phase) + read(phase2 * WINDOW) * window(phase2);
            write = (write + 1) % buf.length;
            return y;
        }

        private float read(double delay) {
            double pos = write - delay - 1;
            while (pos < 0) pos += buf.length;
            int i0 = (int) pos;
            int i1 = (i0 + 1) % buf.length;
            float frac = (float) (pos - i0);
            return buf[i0 % buf.length] * (1 - frac) + buf[i1] * frac;
        }

        private static float window(double p) {
            return (float) (1.0 - Math.abs(2.0 * p - 1.0));
        }
    }

    /** Kammfilter mit Rückkopplung und Dämpfung. */
    private static class Comb {
        private final float[] buf;
        float feedback;
        private final float damp;
        private float store;
        private int pos;

        Comb(int size, double feedback, double damp) {
            this.buf = new float[Math.max(1, size)];
            this.feedback = (float) feedback;
            this.damp = (float) damp;
        }

        float process(float x) {
            float y = buf[pos];
            store = y * (1 - damp) + store * damp;
            buf[pos] = x + store * feedback;
            pos = (pos + 1) % buf.length;
            return y;
        }

        void clear() {
            java.util.Arrays.fill(buf, 0F);
            store = 0;
            pos = 0;
        }
    }

    private static class AllPass {
        private final float[] buf;
        private final float g;
        private int pos;

        AllPass(int size, double g) {
            this.buf = new float[Math.max(1, size)];
            this.g = (float) g;
        }

        float process(float x) {
            float b = buf[pos];
            float y = -x + b;
            buf[pos] = x + b * g;
            pos = (pos + 1) % buf.length;
            return y;
        }

        void clear() {
            java.util.Arrays.fill(buf, 0F);
            pos = 0;
        }
    }

    /** Biquad-Filter (RBJ Cookbook). */
    private static class Biquad {
        private double b0 = 1, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        void lowPass(double freq) {
            set(freq, false);
        }

        void highPass(double freq) {
            set(freq, true);
        }

        private void set(double freq, boolean high) {
            double w0 = 2 * Math.PI * freq / SAMPLE_RATE;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2 * 0.7071);
            double a0 = 1 + alpha;
            if (high) {
                b0 = (1 + cos) / 2;
                b1 = -(1 + cos);
                b2 = (1 + cos) / 2;
            } else {
                b0 = (1 - cos) / 2;
                b1 = 1 - cos;
                b2 = (1 - cos) / 2;
            }
            a1 = -2 * cos;
            a2 = 1 - alpha;
            b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0;
            x1 = x2 = y1 = y2 = 0;
        }

        float process(float x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x;
            y2 = y1; y1 = y;
            return (float) y;
        }
    }
}
