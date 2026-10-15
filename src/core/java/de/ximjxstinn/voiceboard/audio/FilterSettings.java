package de.ximjxstinn.voiceboard.audio;

/**
 * Einstellbare Werte der Voicefilter. Wird in config/voiceboard.json gespeichert.
 */
public class FilterSettings {

    /** Tonhöhe für "Hoch" in % (110-200). */
    public int pitchHigh = 145;
    /** Tonhöhe für "Tief" in % (50-95). */
    public int pitchLow = 72;
    /** Tonhöhe für "Baby" in % (130-220). */
    public int babyPitch = 170;
    /** Modulationsfrequenz für "Roboter" in Hz (20-200). */
    public int robotFrequency = 55;
    /** Verzögerung für "Echo" in ms (50-1000). */
    public int echoDelay = 320;
    /** Wiederholungen beim "Echo" in % (0-90). */
    public int echoFeedback = 45;
    /** Raumgröße für "Hall" in % (0-100). */
    public int hallSize = 55;
    /** Verzerrung für "Radio" in % (0-100). */
    public int radioDistortion = 45;
    /** Klangqualität für "Telefon" in % (0-100). */
    public int phoneQuality = 50;

    public void sanitize() {
        pitchHigh = clamp(pitchHigh, 110, 200);
        pitchLow = clamp(pitchLow, 50, 95);
        babyPitch = clamp(babyPitch, 130, 220);
        robotFrequency = clamp(robotFrequency, 20, 200);
        echoDelay = clamp(echoDelay, 50, 1000);
        echoFeedback = clamp(echoFeedback, 0, 90);
        hallSize = clamp(hallSize, 0, 100);
        radioDistortion = clamp(radioDistortion, 0, 100);
        phoneQuality = clamp(phoneQuality, 0, 100);
    }

    /** Setzt die Werte eines Filters auf Standard zurück. */
    public void resetFor(FilterType type) {
        FilterSettings d = new FilterSettings();
        switch (type) {
            case PITCH_HIGH -> pitchHigh = d.pitchHigh;
            case PITCH_LOW -> pitchLow = d.pitchLow;
            case BABY -> babyPitch = d.babyPitch;
            case ROBOT -> robotFrequency = d.robotFrequency;
            case ECHO -> {
                echoDelay = d.echoDelay;
                echoFeedback = d.echoFeedback;
            }
            case HALL -> hallSize = d.hallSize;
            case RADIO -> radioDistortion = d.radioDistortion;
            case PHONE -> phoneQuality = d.phoneQuality;
            default -> {
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
