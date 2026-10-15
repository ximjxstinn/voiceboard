package de.ximjxstinn.voiceboard.audio;

public enum FilterType {
    NONE("Aus"),
    PITCH_HIGH("Hoch"),
    PITCH_LOW("Tief"),
    BABY("Baby"),
    ROBOT("Roboter"),
    ECHO("Echo"),
    HALL("Hall"),
    RADIO("Radio"),
    PHONE("Telefon");

    public final String label;

    FilterType(String label) {
        this.label = label;
    }
}
