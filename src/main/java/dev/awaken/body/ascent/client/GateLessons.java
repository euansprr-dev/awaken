package dev.awaken.body.ascent.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The gates' way in to the gold figure and to Flow (SPEC §12, §72.6). A gate's line shows a family of moves once to each
 * player with the Way Up's own figure, and a line crossed clean gives a little Flow. Client only.
 */
public final class GateLessons {
    private GateLessons() {}

    /** Ticks the figure waits where it appears before it moves, so you see it first. */
    private static final int WAIT = 4;
    /** How far it goes between two of its points: a sprint on the flat, slower up and down a wall. */
    private static final double FLAT = 0.85, CLIMB = 0.4;

    /**
     * The gold figure walks this path once (feet points in the world). Nothing if a Way Up lesson is showing (that one
     * matters more just then) or reduced motion is on; the words come on the Now line either way.
     */
    public static void show(List<Vec3> path) {
        if (path.size() < 2) return;
        Lesson.figure(pace(path));
    }

    /** A line crossed clean: a little Flow, a bit more for a longer line. */
    public static void clean(int beats) {
        AscentFlow.gain(0.12F + 0.03F * Math.min(5, beats));
    }

    /**
     * The path at the figure's own pace: the figure takes one point every few ticks, and a beat's path names only its
     * turns (a gulf is three points nine blocks apart), so each stretch is cut into steps as long as a sprint takes.
     */
    static Vec3[] pace(List<Vec3> path) {
        List<Vec3> out = new ArrayList<>();
        Vec3 a = path.get(0), b = path.get(1), dir = b.subtract(a).normalize().scale(0.01);
        for (int i = 0; i < WAIT; i++) out.add(a.add(dir.scale(i)));
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 p = path.get(i), q = path.get(i + 1), d = q.subtract(p);
            double flat = Math.sqrt(d.x * d.x + d.z * d.z), up = Math.abs(d.y);
            double step = up > flat ? CLIMB : FLAT;
            int n = Math.max(1, (int) Math.ceil(Math.max(flat, up) / step));
            // the first point is the wait's, every later stretch starts where the last ended
            for (int k = 1; k <= n; k++) out.add(p.lerp(q, k / (double) n));
        }
        return out.toArray(Vec3[]::new);
    }
}
