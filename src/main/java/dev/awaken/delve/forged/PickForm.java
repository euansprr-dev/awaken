package dev.awaken.delve.forged;

import org.jspecify.annotations.Nullable;

/**
 * The four ways a pick can be drawn (SPEC §49.2): one decision at the anvil, changeable by a Re-draw. {@link #speed} is
 * in rock, {@link #soft} in earth and wood (only the Mattock's rules reach those). {@link #hum} is how far it hums toward
 * a lode (0: it doesn't), {@link #damage} what it adds to its rank's blow.
 */
public enum PickForm {
    DELVER("delver", "Delver", 1F, 1F, 1F, 0F, 16, null),
    BREAKER("breaker", "Breaker", 0.85F, 0.85F, 1.3F, 0F, 0, "awaken:delve/deed_heart"),
    MATTOCK("mattock", "Mattock", 0.9F, 1.2F, 1.1F, 0F, 0, null),
    SOUNDER("sounder", "Sounder", 0.9F, 0.9F, 0.8F, -1F, 24, "awaken:delve/deed_lamp");

    public final String id, label;
    public final float speed, soft, uses, damage;
    public final int hum;
    /** The per-player counter that opens it (null: open from the start). */
    public final @Nullable String opens;

    PickForm(String id, String label, float speed, float soft, float uses, float damage, int hum, @Nullable String opens) {
        this.id = id;
        this.label = label;
        this.speed = speed;
        this.soft = soft;
        this.uses = uses;
        this.damage = damage;
        this.hum = hum;
        this.opens = opens;
    }

    /** The form an anvil style names ("pick_delver"), or null when the style isn't a pick's. */
    public static @Nullable PickForm ofStyle(String style) {
        if (!style.startsWith(STYLE)) return null;
        String id = style.substring(STYLE.length());
        for (PickForm f : values()) if (f.id.equals(id)) return f;
        return null;
    }

    public String style() {
        return STYLE + id;
    }

    /** What a pick's style starts with at the War Anvil. */
    public static final String STYLE = "pick_";
}
