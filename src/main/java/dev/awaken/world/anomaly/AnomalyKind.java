package dev.awaken.world.anomaly;

import com.mojang.serialization.Codec;

/** The kinds of small overworld event the anomaly director can start near a player (plan v2 §4.3). */
public enum AnomalyKind {
    /** A crack in the air that spills monsters for about two minutes; break its core to close it. */
    RIFT_TEAR("rift_tear", "Rift tear"),
    /** A short gate at the player's rank (the gates module builds it); fades unless entered, never breaks. */
    WANDERING_GATE("wandering_gate", "Wandering gate"),
    /** Calm, day only: lights that lead the player to a small cache. */
    SPIRIT_PROCESSION("spirit_procession", "Spirit procession");

    public final String id;
    public final String display;

    AnomalyKind(String id, String display) {
        this.id = id;
        this.display = display;
    }

    /** The {@code Progression.discover} key for the first time a player meets this kind. */
    public String discoveryKey() {
        return "anomaly/" + id;
    }

    public static AnomalyKind byId(String id) {
        for (AnomalyKind k : values()) if (k.id.equals(id) || k.name().equalsIgnoreCase(id)) return k;
        return null;
    }

    /** Unknown ids (a kind removed in a later version) load as a rift; interrupted anomalies are cleaned up on load anyway. */
    public static final Codec<AnomalyKind> CODEC = Codec.STRING.xmap(s -> {
        AnomalyKind k = byId(s);
        return k == null ? RIFT_TEAR : k;
    }, k -> k.id);
}
