package dev.awaken.world.anomaly;

/**
 * How a shimmer grove bends the rules while you stand in it (plan v2 §4.3). The ordinal is saved and synced, so new
 * variants go at the end.
 */
public enum GroveVariant {
    /** Low gravity: long, floating jumps and no fall damage inside. */
    LIGHTFOOT("lightfoot", "Light steps", "Your steps are light here", 0xFF9FE8FF),
    /** Ore under the grove glows through the stone for whoever stands in it (drawn by the client). */
    ORE_SIGHT("ore_sight", "Ore sight", "Ore glows through the stone here", 0xFFFFC66B),
    /** A spirit waits at its heart and whispers where something worth finding lies. */
    WHISPER("whisper", "A spirit speaks", "A spirit waits at its heart", 0xFFC9B2FF),
    /** Flowers spring up and mend whoever stands among them. */
    BLOOM("bloom", "Healing flowers", "Flowers here mend you", 0xFFFFA8D6);

    public final String id;
    public final String label;
    public final String blurb;
    /** ARGB tint for the shimmer and its sparkles. */
    public final int colour;

    GroveVariant(String id, String label, String blurb, int colour) {
        this.id = id;
        this.label = label;
        this.blurb = blurb;
        this.colour = colour;
    }

    public static GroveVariant of(int ordinal) {
        GroveVariant[] v = values();
        return v[Math.floorMod(ordinal, v.length)];
    }

    public static GroveVariant byId(String id) {
        for (GroveVariant v : values()) if (v.id.equals(id) || v.name().equalsIgnoreCase(id)) return v;
        return null;
    }
}
