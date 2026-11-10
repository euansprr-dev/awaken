package dev.awaken.powers.fx;

import net.minecraft.world.entity.player.Player;

/**
 * How a cast is made, by what the body is doing at the press (SPEC §32, "Ways"; Euan, 9 Nov 2026: reward the situation, never a
 * timing window). The hands decide it and the server keeps it when it could be true; a way changes a power's gesture and its
 * shape, never its damage. Powers that have none cast the standing way whatever is claimed.
 * <p>
 * A take is which of a gesture's versions plays (0 to 2), counted by the hands so the same one never comes twice running.
 * Both ride the cast payload's key bits and every event of the cast, so everyone sees the cast the caster sees.
 */
public final class Ways {
    private Ways() {}

    public static final int STAND = 0, RUN = 1, AIR = 2;
    /** Clip name suffixes, by way. */
    private static final String[] SUFFIX = {"", "__run", "__air"};

    /** Bits in the cast payload's keys (above the four movement keys) and in an effect event's flags. */
    public static final int KEY_WAY_SHIFT = 4, KEY_TAKE_SHIFT = 6, FLAG_WAY_SHIFT = 12, FLAG_TAKE_SHIFT = 14;

    public static int pack(int keys, int way, int take) {
        return (keys & 15) | ((way & 3) << KEY_WAY_SHIFT) | ((take & 3) << KEY_TAKE_SHIFT);
    }

    public static int wayOf(int keys) {
        int w = (keys >> KEY_WAY_SHIFT) & 3;
        return w > AIR ? STAND : w;
    }

    public static int takeOf(int keys) {
        return Math.min(2, (keys >> KEY_TAKE_SHIFT) & 3);
    }

    public static int flags(int way, int take) {
        return ((way & 3) << FLAG_WAY_SHIFT) | ((take & 3) << FLAG_TAKE_SHIFT);
    }

    public static int wayOfFlags(int flags) {
        int w = (flags >> FLAG_WAY_SHIFT) & 3;
        return w > AIR ? STAND : w;
    }

    public static int takeOfFlags(int flags) {
        return Math.min(2, (flags >> FLAG_TAKE_SHIFT) & 3);
    }

    /** What the body is doing now, on either side: in the air, at a sprint or a slide, or standing. */
    public static int of(Player p) {
        if (!p.onGround() && !p.isInWater() && !p.onClimbable() && !p.isPassenger() && !p.getAbilities().flying) return AIR;
        if (p.isSprinting() || dev.awaken.core.api.MovementHooks.sliding(p) || dev.awaken.core.api.MovementHooks.tacticalSprinting(p)) return RUN;
        return STAND;
    }

    /** A clip's name for a way and a take: {@code base[__run|__air][_t2|_t3]}. */
    public static String clip(String base, int way, int take) {
        return base + SUFFIX[way < 0 || way > AIR ? 0 : way] + (take <= 0 ? "" : "_t" + (take + 1));
    }
}
