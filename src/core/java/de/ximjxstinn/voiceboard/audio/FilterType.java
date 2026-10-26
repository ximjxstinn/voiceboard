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
    PHONE("Telefon"),
    VILLAGER("Villager"),
    ENDERMAN("Enderman"),
    ZOMBIE("Zombie"),
    GHAST("Ghast"),
    IRON_GOLEM("Eisengolem"),
    ALIEN("Alien"),
    UNDERWATER("Unterwasser");

    public final String label;

    FilterType(String label) {
        this.label = label;
    }
}
