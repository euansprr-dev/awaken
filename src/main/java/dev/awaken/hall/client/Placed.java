package dev.awaken.hall.client;

import dev.awaken.armour.ArmourSet;
import org.jspecify.annotations.Nullable;

/**
 * One thing in a picture: stage pixels for its middle, stage pixels to a mesh unit, its turn in degrees, how to draw it
 * (the shader's modes: 0 itself, 1 a shape, 2 dim, 3 iron) and how solid. A suit ({@code set} not null) has a mode for each
 * piece, a nibble each, helm lowest; {@link #MARKED} on a piece gives it the lantern's edge; depth brings it nearer.
 */
record Placed(String id, float x, float y, float scale, float yaw, float pitch, float roll, int mode, float alpha, @Nullable ArmourSet set, float depth) {
    static final int FULL = 0, SHAPE = 1, DIM = 2, IRON = 3, SKIP = 7, MARKED = 8;

    Placed(String id, float x, float y, float scale, float yaw, float pitch, float roll, int mode, float alpha) {
        this(id, x, y, scale, yaw, pitch, roll, mode, alpha, null, 0F);
    }

    static Placed suit(ArmourSet set, float x, float y, float depth, float scale, float yaw, float pitch, int modes, float alpha) {
        return new Placed(set.id, x, y, scale, yaw, pitch, 0F, modes, alpha, set, depth);
    }

    /** All four pieces drawn the same way. */
    static int all(int mode) {
        return mode * 0x1111;
    }
}
