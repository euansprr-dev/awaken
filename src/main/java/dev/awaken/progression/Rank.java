package dev.awaken.progression;

import com.mojang.serialization.Codec;

/**
 * Hunter ranks. Numbers come from tools/sim/pacing_sim.py (SPEC section 2). v2 shares (2026-10-23): E .28 so the first
 * hour lands at level 5–6 with the prologue banked; D .16, C .065 so level 10 ≈ 3.5 h, 25 ≈ 17 h, 50 ≈ 56 h; B .22 and
 * A and above more again so the climb from 50 to 135 takes ~45 h and the story ends near 100 h (docs/modules/onboarding.md).
 */
public enum Rank {
    E("E", 0, 5, 0.28, 0xF4F6FB),
    D("D", 10, 17, 0.16, 0x5FD08F),
    C("C", 25, 35, 0.065, 0x6FB3FF),
    B("B", 45, 57, 0.22, 0xB48CFF),
    A("A", 70, 85, 0.60, 0xF2A541),
    S("S", 100, 118, 0.55, 0xFF5A6A),
    SOVEREIGN("Sovereign", 135, 150, 0.50, 0xD4AF37);

    public static final Codec<Rank> CODEC = dev.awaken.core.Codecs.lenientEnum(Rank.class, E);

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
