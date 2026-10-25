package dev.awaken.arms;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A swing's pose as a move on Hands' view layer (SPEC §25 "First person"; docs/anims/PLAN.md §9). Arms poses the hand
 * about its own place (moved by {@code d}, the blade rolled in the screen's plane, its tip tilted forward); Hands'
 * view layer moves the view before vanilla's hand transform, by a translation then pitch, yaw and roll about the eye.
 * This turns the one into the other exactly: the same rotation as XYZ angles, and the translation that keeps the
 * hand's pivot where Arms put it. Pure maths, no client classes, so a GameTest can hold it to the matrix.
 */
public final class HandPose {
    private HandPose() {}

    private static final Quaternionf Q = new Quaternionf();
    private static final Vector3f V = new Vector3f();

    /**
     * For a hand resting at {@code (hx, hy, hz)} (view space, blocks), moved by {@code (dx, dy, dz)}, rolled {@code roll}
     * degrees about the view's axis and tilted {@code tilt} degrees tip-forward: Hands' view move {x, y, z, pitch, yaw,
     * roll} into {@code out[0..6)}, applied before the hand's own translation to the rest place. Client thread only.
     */
    public static void viewMove(float hx, float hy, float hz, float dx, float dy, float dz, float roll, float tilt, float[] out) {
        Q.identity().rotateZ(roll * DEG).rotateX(-tilt * DEG);
        Q.getEulerAnglesXYZ(V);
        float px = V.x, py = V.y, pz = V.z;
        // the rest place turned by the rotation, so the translation puts the turned hand back on its own pivot
        V.set(hx, hy, hz);
        Q.transform(V);
        out[0] = hx + dx - V.x;
        out[1] = hy + dy - V.y;
        out[2] = hz + dz - V.z;
        out[3] = px / DEG;
        out[4] = py / DEG;
        out[5] = pz / DEG;
    }

    private static final float DEG = (float) (Math.PI / 180);
}
