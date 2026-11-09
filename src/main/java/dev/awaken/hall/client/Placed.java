package dev.awaken.hall.client;

import dev.awaken.armour.ArmourSet;
import org.jspecify.annotations.Nullable;

/**
 * One thing in a picture: stage pixels for its middle, stage pixels to a mesh unit, its turn in degrees, how to draw it
 * (the shader's modes: 0 itself, 1 a shape, 2 dim, 3 iron) and how solid. A suit ({@code set} not null) has a mode for each
 * piece, a nibble each, helm lowest; {@link #MARKED} on a piece gives it the lantern's edge; depth brings it nearer. A weapon's
 * mode can carry {@link #lit}: how hot the steel is, how far the heat has reached, and a flash when the work lands.
 */
record Placed(String id, float x, float y, float scale, float yaw, float pitch, float roll, int mode, float alpha, @Nullable ArmourSet set, float depth, int accent) {
    static final int FULL = 0, SHAPE = 1, DIM = 2, IRON = 3, SKIP = 7, MARKED = 8;

    Placed(String id, float x, float y, float scale, float yaw, float pitch, float roll, int mode, float alpha) {
        this(id, x, y, scale, yaw, pitch, roll, mode, alpha, null, 0F, -1);
    }

    /** A weapon drawn in front of whatever's at a smaller depth, its accent {@code 0xRRGGBB} (a Force's) or -1 for its own. */
    static Placed weapon(String id, float x, float y, float scale, float yaw, float pitch, float roll, int mode, float alpha, float depth, int accent) {
        return new Placed(id, x, y, scale, yaw, pitch, roll, mode, alpha, null, depth, accent);
    }

    static Placed suit(ArmourSet set, float x, float y, float depth, float scale, float yaw, float pitch, int modes, float alpha) {
        return new Placed(set.id, x, y, scale, yaw, pitch, 0F, modes, alpha, set, depth, -1);
    }

    /**
     * A weapon's mode with heat on it (0..1 each). {@code front}: 1 only the tip, 0 the whole length. {@code force}: the heat
     * takes the weapon's accent colour instead of the fire's (a Force being set); the accent comes from {@code accent}.
     */
    static int lit(int mode, float heat, float front, boolean force, float flash) {
        int h = Math.clamp(Math.round(heat * 31F), 0, 31), f = Math.clamp(Math.round(front * 31F), 0, 31), l = Math.clamp(Math.round(flash * 31F), 0, 31);
        return mode | h << 4 | f << 9 | (force ? 1 << 14 : 0) | l << 16;
    }

    /** All four pieces drawn the same way. */
    static int all(int mode) {
        return mode * 0x1111;
    }
}
