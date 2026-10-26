package dev.awaken.core.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * First person for every module (SPEC §24, "Hands"; docs/anims/PLAN.md): the one owner of what your own body looks
 * like through your eyes. Movement, interactions and combat all reach it here, never through their own hand events.
 * <p>
 * Three layers, composed every frame by the {@code anims} client (which installs itself here):
 * <ul>
 *   <li><b>World</b>: the body drawn in the world with real depth, its limbs anchored to what they touch (hands on a
 *   pipe, feet on a wall). A {@link WorldPose} fills a {@link Rig}; the highest priority source that poses this frame
 *   wins, and while it shows arms the vanilla hands step aside.</li>
 *   <li><b>View</b>: moves on top of vanilla's own hands and held items, in view space. Every {@link ViewPose} adds to a
 *   {@link View} each frame (stances: a sprint lowers the hands, a rope lifts one).</li>
 *   <li><b>Clips</b>: one-shot or looped view clips from {@code assets/awaken/fp_clips/*.json}, written in Python
 *   ({@code tools/art/specs/fp_moves.py}), sampled with {@code dev.awaken.anims.Ease}'s curves; {@link #play} starts
 *   one, its keyed events reach every {@link Events} listener (a sound on a strike, a hit-stop on an impact).</li>
 * </ul>
 * Holds no client classes: common code may name it; on a server (or with the anims client absent) everything is a
 * no-op. Registration lists are read every frame by the client; register once at init.
 */
public final class FirstPerson {
    private FirstPerson() {}

    // ---- the world layer --------------------------------------------------------------------------------------------

    /** A part of the body drawn in the world: the arms and legs from their roots, the torso from the neck. */
    public enum Limb { RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, BODY }

    /**
     * The body in the world for one frame (world coordinates, plain arrays: nothing allocated per frame). Each limb is a
     * rigid part drawn from its root (shoulder, hip, neck) toward its tip (the hand, the foot, the hips); {@code front}
     * is the way the limb's front faces (a hanging arm's: the body's forward), {@code weight} 0..1 how much of it shows.
     * Right and left are the body's own, not the main hand's.
     */
    public static final class Rig {
        public static final int N = Limb.values().length;
        public final double[] root = new double[N * 3], tip = new double[N * 3], front = new double[N * 3];
        public final float[] weight = new float[N];
        /** Where the body faces (horizontal, unit). */
        public double fx, fz = 1;
        /** What each hand holds, drawn in it (right, left; empty for nothing): a plucked flower, food being laid down. */
        public final net.minecraft.world.item.ItemStack[] held = {net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY};
        /**
         * How far from the eye the arms are drawn (blocks; 0 for a hand's usual 0.85): nearer reads bigger. Each fist
         * stays where the world has it on screen, wherever this puts it.
         */
        public double handDistance;
        /** How lit the hands are from within (0..1). */
        public float glow;

        public void clear() {
            java.util.Arrays.fill(weight, 0F);
            held[0] = held[1] = net.minecraft.world.item.ItemStack.EMPTY;
            handDistance = 0;
            glow = 0;
        }

        public void limb(Limb l, double rx, double ry, double rz, double tx, double ty, double tz, double frx, double fry, double frz, float w) {
            int i = l.ordinal(), j = i * 3;
            root[j] = rx;
            root[j + 1] = ry;
            root[j + 2] = rz;
            tip[j] = tx;
            tip[j + 1] = ty;
            tip[j + 2] = tz;
            front[j] = frx;
            front[j + 1] = fry;
            front[j + 2] = frz;
            weight[i] = w;
        }

        /**
         * A limb whose end (a fist, a foot) sits exactly at (hx, hy, hz), pointing from near its joint (sx, sy, sz): the
         * limb is rigid, so its root slides along that line to where its own reach lands the end on the spot. Reaches:
         * {@link #ARM_REACH}, {@link #LEG_REACH}.
         */
        public void reach(Limb l, double sx, double sy, double sz, double hx, double hy, double hz, double reach,
                          double frx, double fry, double frz, float w) {
            double dx = hx - sx, dy = hy - sy, dz = hz - sz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1e-4) {
                dx = 0;
                dy = -1;
                dz = 0;
                d = 1;
            }
            limb(l, hx - dx / d * reach, hy - dy / d * reach, hz - dz / d * reach, hx, hy, hz, frx, fry, frz, w);
        }

        /** From a limb's root to the middle of its fist (an arm) or under its foot (a leg), in blocks. */
        public static final double ARM_REACH = 0.56, LEG_REACH = 0.72;

        public boolean arms() {
            return weight[Limb.RIGHT_ARM.ordinal()] > 0.01F || weight[Limb.LEFT_ARM.ordinal()] > 0.01F;
        }

        public boolean any() {
            for (float w : weight) if (w > 0.01F) return true;
            return false;
        }
    }

    /** A source of the world body: fills the rig and returns true when it poses the body this frame. */
    @FunctionalInterface
    public interface WorldPose {
        boolean pose(float partialTick, Rig rig);
    }

    public record WorldSource(String id, int priority, WorldPose pose) {}

    private static final List<WorldSource> WORLD = new CopyOnWriteArrayList<>();

    /** Registers a world source (higher priority is asked first). Movement uses 100, interactions 200, combat 300. */
    public static void world(String id, int priority, WorldPose pose) {
        WORLD.removeIf(s -> s.id().equals(id));
        WORLD.add(new WorldSource(id, priority, pose));
        WORLD.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    }

    public static List<WorldSource> worldSources() {
        return WORLD;
    }

    // ---- the view layer ---------------------------------------------------------------------------------------------

    /**
     * Moves on top of vanilla's hands, in view space (x right, y up, z toward the viewer, blocks; degrees about x, y,
     * z, applied in that order after the move). {@code both} moves both hands; {@code main} and {@code off} each hand.
     * The off arm is drawn even with nothing in it while {@code drawOff} is set (two-handed moves).
     */
    public static final class View {
        public static final int X = 0, Y = 1, Z = 2, PITCH = 3, YAW = 4, ROLL = 5;
        public final float[] both = new float[6], main = new float[6], off = new float[6];
        /**
         * Fists (an empty hand only): where on screen a hand is, in view space (x, y, z, then the summed weight). While a
         * hand has one, its arm is drawn rising to it from under the frame's corner instead of vanilla's arm moved by
         * offsets, so a hand can go anywhere without its forearm floating into view. Vanilla's own resting fist is at
         * {@link #REST_FIST} (mirrored for the left hand).
         */
        public final float[] fistMain = new float[4], fistOff = new float[4];
        /**
         * Grips (a hand that holds something): where the held thing's grip is this frame, in view space (x, y, z, then the
         * summed weight), as {@link #fistMain} is for an empty hand. While a hand has one, its arm is drawn rising to the
         * grip from under the frame's corner, and what it holds still draws: a weapon swung far from vanilla's resting
         * place is held, not floating. Unset (weight 0), nothing changes.
         */
        public final float[] gripMain = new float[4], gripOff = new float[4];
        public boolean drawOff, hideMain, hideOff;
        /** A colour the fists' arms are tinted (RGB; 0 for none): the Sovereign's gold. */
        public int tint;
        /** How lit the fists are from within (0..1, the most of the sources): a palm on a waystone, a charging cast. */
        public float glow;

        /** Vanilla's resting empty right fist, in view space. */
        public static final float[] REST_FIST = {0.744F, -0.455F, -1.072F};

        public void clear() {
            java.util.Arrays.fill(both, 0F);
            java.util.Arrays.fill(main, 0F);
            java.util.Arrays.fill(off, 0F);
            java.util.Arrays.fill(fistMain, 0F);
            java.util.Arrays.fill(fistOff, 0F);
            java.util.Arrays.fill(gripMain, 0F);
            java.util.Arrays.fill(gripOff, 0F);
            drawOff = hideMain = hideOff = false;
            tint = 0;
            glow = 0;
        }

        /** Adds a fist (weighted: several sum to their weighted mean). */
        public static void fist(float[] to, float k, float x, float y, float z) {
            if (k <= 0) return;
            to[X] += x * k;
            to[Y] += y * k;
            to[Z] += z * k;
            to[3] += k;
        }

        /** Adds a move (scaled by k) to one of the three. */
        public static void add(float[] to, float k, float x, float y, float z, float pitch, float yaw, float roll) {
            to[X] += x * k;
            to[Y] += y * k;
            to[Z] += z * k;
            to[PITCH] += pitch * k;
            to[YAW] += yaw * k;
            to[ROLL] += roll * k;
        }

        public boolean moved() {
            for (int i = 0; i < 6; i++) if (both[i] != 0 || main[i] != 0 || off[i] != 0) return true;
            return drawOff || hideMain || hideOff || fistMain[3] > 0 || fistOff[3] > 0 || gripMain[3] > 0 || gripOff[3] > 0;
        }
    }

    /** A stance: adds this frame's moves to the view. */
    @FunctionalInterface
    public interface ViewPose {
        void pose(float partialTick, View view);
    }

    private static final List<ViewPose> VIEW = new CopyOnWriteArrayList<>();

    public static void view(ViewPose pose) {
        VIEW.add(pose);
    }

    public static List<ViewPose> viewSources() {
        return VIEW;
    }

    // ---- clips and their events -------------------------------------------------------------------------------------

    /** A clip's keyed event reached (the clip's name and the event's). */
    @FunctionalInterface
    public interface Events {
        void on(String clip, String event);
    }

    private static final List<Events> EVENTS = new CopyOnWriteArrayList<>();

    public static void listen(Events e) {
        EVENTS.add(e);
    }

    /** For the client: tells every listener (never throws: a broken listener is skipped). */
    public static void fire(String clip, String event) {
        for (Events e : EVENTS) {
            try {
                e.on(clip, event);
            } catch (RuntimeException ignored) {
                // a listener's failure is its own
            }
        }
    }

    /** What plays the clips (the anims client). */
    public interface Player {
        /** Starts a view clip (again from its start); speed 1 is as written. False when there's no such clip. */
        boolean play(String clip, float speed);

        void stop(String clip);

        boolean playing(String clip);

        /** The world body is drawn this frame (its arms: vanilla's hands stepped aside). */
        boolean armsInWorld();
    }

    private static volatile Player player;

    public static void install(Player p) {
        player = p;
    }

    public static boolean play(String clip) {
        return play(clip, 1F);
    }

    public static boolean play(String clip, float speed) {
        Player p = player;
        return p != null && p.play(clip, speed);
    }

    public static void stop(String clip) {
        Player p = player;
        if (p != null) p.stop(clip);
    }

    public static boolean playing(String clip) {
        Player p = player;
        return p != null && p.playing(clip);
    }

    public static boolean armsInWorld() {
        Player p = player;
        return p != null && p.armsInWorld();
    }

    /** The station scenes' switch (the feel settings' stationScenes): the crafting table worked by hand. */
    public static volatile java.util.function.BooleanSupplier stationsOn = () -> true;

    /** The touches' switch (the feel settings' firstPersonTouches): hands on the things you use. */
    public static volatile java.util.function.BooleanSupplier touchesOn = () -> true;

    /** The comfort setting (the body's reduced motion): halves the procedural layer (look-lag) when true. */
    public static volatile java.util.function.BooleanSupplier reducedMotion = () -> false;

    /** Names of every registered source (dev command). */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (WorldSource s : WORLD) out.add("world " + s.id() + " @" + s.priority());
        out.add(VIEW.size() + " view stances, " + EVENTS.size() + " event listeners");
        return out;
    }
}
