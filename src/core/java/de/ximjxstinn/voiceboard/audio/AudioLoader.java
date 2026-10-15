package de.ximjxstinn.voiceboard.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Lädt .mp3- und .wav-Dateien und wandelt sie in 48 kHz Mono-PCM (16 Bit) um,
 * das Format, das Simple Voice Chat erwartet.
 */
public final class AudioLoader {

    private AudioLoader() {
    }

    public static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".wav");
    }

    public static short[] load(Path path) throws Exception {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) {
            return loadMp3(path);
        }
        return loadWav(path);
    }

    private static short[] loadMp3(Path path) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();
            ShortList samples = new ShortList();
            int channels = 2;
            int rate = 44100;
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                channels = decoder.getOutputChannels();
                rate = decoder.getOutputFrequency();
                samples.add(out.getBuffer(), out.getBufferLength());
                bitstream.closeFrame();
            }
            bitstream.close();
            return toMono48k(samples.toArray(), channels, rate);
        }
    }

    private static short[] loadWav(Path path) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            AudioFormat src = source.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, source)) {
                byte[] bytes = readAll(pcm);
                short[] samples = new short[bytes.length / 2];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
                }
                return toMono48k(samples, target.getChannels(), Math.round(target.getSampleRate()));
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    /** Stereo -> Mono und lineares Resampling auf 48 kHz. */
    private static short[] toMono48k(short[] interleaved, int channels, int rate) {
        channels = Math.max(1, channels);
        int frames = interleaved.length / channels;
        float[] mono = new float[frames];
        for (int f = 0; f < frames; f++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += interleaved[f * channels + c];
            }
            mono[f] = (float) sum / channels;
        }
        if (rate == VoiceFilter.SAMPLE_RATE || frames == 0) {
            short[] out = new short[frames];
            for (int i = 0; i < frames; i++) out[i] = (short) mono[i];
            return out;
        }
        double step = (double) rate / VoiceFilter.SAMPLE_RATE;
        int outLength = (int) (frames / step);
        short[] out = new short[outLength];
        for (int i = 0; i < outLength; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, frames - 1);
            double frac = pos - i0;
            out[i] = (short) Math.round(mono[i0] * (1 - frac) + mono[i1] * frac);
        }
        return out;
    }

    private static class ShortList {
        private short[] data = new short[1 << 16];
        private int size;

        void add(short[] src, int length) {
            if (size + length > data.length) {
                short[] bigger = new short[Math.max(data.length * 2, size + length)];
                System.arraycopy(data, 0, bigger, 0, size);
                data = bigger;
            }
            System.arraycopy(src, 0, data, size, length);
            size += length;
        }

        short[] toArray() {
            short[] out = new short[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }
    }
}
