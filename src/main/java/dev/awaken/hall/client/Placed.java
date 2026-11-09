package dev.awaken.hall.client;

/**
 * One weapon in a picture: stage pixels for its middle, stage pixels to a mesh unit, its turn in degrees, how to draw it
 * (the shader's modes: 0 itself, 1 a shape, 2 dim, 3 iron) and how solid.
 */
record Placed(String id, float x, float y, float scale, float yaw, float pitch, float roll, int mode, float alpha) {
    static final int FULL = 0, SHAPE = 1, DIM = 2, IRON = 3;
}
