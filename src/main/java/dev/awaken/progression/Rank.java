package dev.awaken.progression;

import com.mojang.serialization.Codec;

/** Hunter ranks. Numbers come from tools/sim/pacing_sim.py (SPEC section 2). */
public enum Rank {
    E("E", 0, 5, 0.81, 0xF4F6FB),
    D("D", 10, 17, 0.39, 0x5FD08F),
    C("C", 25, 35, 0.25, 0x6FB3FF),
    B("B", 45, 57, 0.23, 0xB48CFF),
    A("A", 70, 85, 0.18, 0xF2A541),
    S("S", 100, 118, 0.12, 0xFF5A6A),
    SOVEREIGN("Sovereign", 135, 150, 0.12, 0xD4AF37);

    public static final Codec<Rank> CODEC = Codec.STRING.xmap(Rank::valueOf, Rank::name);

    public final String label;
    /** Level needed to take this rank's Reassessment. */
    public final int levelRequired;
    /** Content level for gates and strata of this rank. */
    public final int contentLevel;
    /** Share of a level one cleared gate floor pays. */
    public final double share;
    public final int colour;

    Rank(String label, int levelRequired, int contentLevel, double share, int colour) {
        this.label = label;
        this.levelRequired = levelRequired;
        this.contentLevel = contentLevel;
        this.share = share;
        this.colour = colour;
    }

    public Rank next() {
        return this == SOVEREIGN ? null : values()[ordinal() + 1];
    }

    public Rank below() {
        return this == E ? E : values()[ordinal() - 1];
    }

    public Rank above() {
        return this == SOVEREIGN ? SOVEREIGN : values()[ordinal() + 1];
    }

    public static Rank of(int ordinal) {
        return values()[Math.max(0, Math.min(values().length - 1, ordinal))];
    }
}
