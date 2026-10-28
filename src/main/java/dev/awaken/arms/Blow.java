package dev.awaken.arms;

import net.minecraft.util.Mth;

/**
 * One landed blow, described once on the server and answered by every rule, renderer and sound (SPEC §25 "Fight
 * Feel"): the way the blade went in the world, whether it pins or lifts, how heavy it was, whether it killed. It rides
 * in HIT's aux2 (arms_result for the striker, arms_seen for everyone near), packed in 29 bits so the payload is
 * unchanged: the move (8), the flags (4), the bodies this move has struck (3), the lift (2), the travel's yaw (8) and
 * the cut's sideways share (4).
 *
 * @param move     the move's ordinal ({@link Archetype.Move#byId})
 * @param kill     the blow killed
 * @param last     ...and no other hostile within 16 blocks hunts the striker (the fight is over)
 * @param strong   a finisher, a perfect heavy or an Execution (its weight ×1.3)
 * @param cleave   the bodies this move has struck, this one included (1..7)
 * @param lift     -1 pinned (a chop, a slam, an overhead), 0 level, +1 lifted (an uppercut, a rise)
 * @param yaw      the push's direction in the world, a byte of a full turn (0: +z, as Minecraft's yaw)
 * @param side     the cut's sideways share on screen, -7 (right to left) .. +7 (left to right); 0 for a thrust
 */
public record Blow(int move, boolean kill, boolean last, boolean strong, int cleave, int lift, int yaw, int side) {
    public static final int KILL = 1 << 8, LAST = 1 << 9, STRONG = 1 << 10;

    public int pack() {
        return (move & 0xFF) | (kill ? KILL : 0) | (last ? LAST : 0) | (strong ? STRONG : 0)
                | (Mth.clamp(cleave, 0, 7) << 12) | ((Mth.clamp(lift, -1, 1) + 1) << 15) | ((yaw & 0xFF) << 17)
                | ((Mth.clamp(side, -7, 7) + 7) << 25);
    }

    public static Blow unpack(int v) {
        return new Blow(v & 0xFF, (v & KILL) != 0, (v & LAST) != 0, (v & STRONG) != 0, (v >> 12) & 7, ((v >> 15) & 3) - 1,
                (v >> 17) & 0xFF, ((v >> 25) & 0xF) - 7);
    }

    /** The move alone, for the readers that only need it (the impact's sound, the sparks). */
    public static int moveOf(int packed) {
        return packed & 0xFF;
    }

    /** A blow with only its move known (an old reader's call, a thing struck that isn't alive). */
    public static Blow of(int move) {
        return new Blow(move, false, false, false, 1, 0, 0, 0);
    }

    /** The push's direction as a flat unit vector: x. */
    public float dirX() {
        return -Mth.sin(yaw * Mth.TWO_PI / 256F);
    }

    public float dirZ() {
        return Mth.cos(yaw * Mth.TWO_PI / 256F);
    }

    /** The cut's sideways share, -1..1. */
    public float sideShare() {
        return side / 7F;
    }

    /** A flat direction (x, z) as the byte {@link #yaw} stores. */
    public static int yawByte(double x, double z) {
        float deg = (float) (Mth.atan2(-x, z) * Mth.RAD_TO_DEG);
        return Math.round(deg / 360F * 256F) & 0xFF;
    }
}
