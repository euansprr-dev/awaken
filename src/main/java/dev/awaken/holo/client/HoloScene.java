package dev.awaken.holo.client;

import org.joml.Vector3f;

/**
 * The band in a scene of its own (SPEC §30.5, the map in the world): while the map scene holds the camera, your wrist
 * stays raised in third person, beams run from the band to the near edge of the map, and other players see the MAP
 * kind (a content-free disc of light). The scene sets this each frame; nothing here draws the map itself. Client only.
 */
public final class HoloScene {
    private HoloScene() {}

    private static float raise;
    private static boolean open;
    private static boolean beams;
    // where the beams land, in normalised device coordinates (x right, y up), and how far from the eye
    private static float ax, ay, bx, by, depth = 4.5f;

    /** The map scene's raise (0..1) and whether it is opening or open (false while it folds away). */
    public static void map(float raise, boolean open) {
        HoloScene.raise = Math.clamp(raise, 0f, 1f);
        HoloScene.open = open && raise > 0f;
        if (raise <= 0f) beams = false;
    }

    /** Beams from the band to two points of the screen (NDC, y up), {@code depth} metres down the view. */
    public static void beams(float ax, float ay, float bx, float by, float depth) {
        HoloScene.ax = ax;
        HoloScene.ay = ay;
        HoloScene.bx = bx;
        HoloScene.by = by;
        HoloScene.depth = depth;
        beams = true;
    }

    public static void noBeams() {
        beams = false;
    }

    /** Whether a scene may take the camera at all: reduced motion keeps every view where it is. */
    public static boolean allowed() {
        return HoloWorld.allowed();
    }

    static float raise() {
        return raise;
    }

    static boolean mapOpen() {
        return open;
    }

    static boolean hasBeams() {
        return beams && raise > 0f;
    }

    /** The beams' two ends as camera-relative world points this frame. */
    static void beamEnds(Vector3f a, Vector3f b) {
        unproject(ax, ay, depth, a);
        unproject(bx, by, depth, b);
    }

    /** A point of the screen (NDC, y up) {@code d} metres down the view axis, camera-relative world. */
    static void unproject(float nx, float ny, float d, Vector3f out) {
        out.set(nx * ViewMath.tanX * d, ny * ViewMath.tanY * d, -d);
        ViewMath.VIEW_TO_WORLD.transformDirection(out);
    }

    /** Logout, dimension change. */
    static void reset() {
        raise = 0;
        open = false;
        beams = false;
    }
}
