package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Ascension trials staged in gates (SPEC §17.10): the gate runs the course and tells the movement module where a
 * runner is in it; the movement module gives the tier and plays the trial's moments. Installed by the Ascent; the
 * defaults give nothing and cross nothing, so a trial gate runs as a plain course without it.
 */
public final class TrialHooks {
    private TrialHooks() {}

    /** The Leaper's Gauntlet: the course whose last chasm only a second jump crosses. */
    public static final String LEAPER = "awaken:leapers_gauntlet";
    /** The Sky Leviathan's trial: the Lord of the sky, fought on the wing (its edge: the arena's door). */
    public static final String SKYBORN = "awaken:sky_leviathans_trial";
    /** The first Proving (SPEC §47.4): the Ringer beaten, its chain is the rope; {@code done} gives it. */
    public static final String RINGER = "awaken:the_ringer";
    /** The fourth Proving (SPEC §47.8): the roof lifted, {@code atEdge} gives Skyborn; the Sky Warden beaten, {@code done}
     * gives the sky's gift (as the Sky Leviathan's trial does). */
    public static final String SKY_WARDEN = "awaken:the_sky_warden";

    public interface Trials {
        /**
         * A runner reached the trial's edge (the chasm). Whether they now hold what crosses it: the trial gives it here,
         * the first time, with its moment.
         */
        default boolean atEdge(ServerPlayer p, String trial) { return false; }

        /** A runner crossed and reached the far landing: the trial is done ({@code ticks} on the clock, medal 1..3). */
        default void done(ServerPlayer p, String trial, int ticks, int medal) {}

        /**
         * A runner entered a named mark of a trial's course (SPEC §71.4), with the two ends of the thing marked (a
         * gulf's lips, a spot's take-off and landing; the same block twice for a point). Both are world block positions
         * of the cell feet stand in (the one above the floor), not of the floor. The Leaper's Gauntlet's marks:
         * {@link #MARK_REACH}, {@link #MARK_USE} + "double/1" (and "dash/2", "slam/3", ...), {@link #MARK_MISS} or
         * "miss/double" (sent once for every three falls at one spot: the spot is shown again) and {@link #MARK_RUN}
         * (the timed run's first step).
         */
        default void at(ServerPlayer p, String trial, String mark, BlockPos from, BlockPos to) {}

        /**
         * A runner finished a course that pays through its trial (§71.4: the far end of the Gauntlet's timed run). The
         * trial gives what it gives ({@code first}: nobody has paid this runner for it before) and returns what was paid,
         * for the course to show.
         */
        default Pay pay(ServerPlayer p, String trial, int ticks, int medal) { return Pay.NONE; }

        /** Whether this runner has finished the trial before (the course then starts at its timed run). */
        default boolean finished(ServerPlayer p, String trial) { return false; }
    }

    /** The marks of {@link Trials#at}. */
    public static final String MARK_REACH = "reach", MARK_USE = "use/", MARK_MISS = "miss", MARK_RUN = "run";

    /**
     * What a trial paid at its end: XP, the mastery level before and after and how full the new level is (0..1), and how
     * often each of its moves was made in the run, in the trial's own order with their plain names ("Double jumps").
     */
    public record Pay(boolean first, int xp, int masteryBefore, int masteryAfter, float masteryFill, List<Count> counts) {
        public static final Pay NONE = new Pay(false, 0, 0, 0, 0F, List.of());

        public record Count(String name, int times) {}
    }

    /** A slam landed (the Ascent's dive slam): where, and how hard (its speed, blocks a tick). */
    public interface SlamListener {
        void landed(ServerPlayer p, double x, double y, double z, float speed);
    }

    private static final List<SlamListener> SLAMS = new CopyOnWriteArrayList<>();

    /** A course that answers a slam (a deck that breaks, a plate that frees a hammer) listens here. */
    public static void onSlam(SlamListener l) {
        SLAMS.add(l);
    }

    /** The Ascent says a slam landed. */
    public static void slamLanded(ServerPlayer p, double x, double y, double z, float speed) {
        for (SlamListener l : SLAMS) l.landed(p, x, y, z, speed);
    }

    /**
     * A runner took what a stand of a trial holds (§71.3: the Gauntlet's stands; {@code move} is "double", "dash", "slam"
     * or "dive"). Called once, on the server, on the tick the stand is taken: the runner's body is then held for 40
     * ticks and the move's title shows 12 ticks in. The course answers with its own stroke and opens the way on from
     * that stand. A door is real blocks, so it opens for everyone in the gate; the hall past it is what asks for the
     * move, and every runner still takes each stand for themselves.
     */
    public interface StandListener {
        void taken(ServerPlayer p, String trial, String move, BlockPos stand);
    }

    private static final List<StandListener> STANDS = new CopyOnWriteArrayList<>();

    public static void onStand(StandListener l) {
        STANDS.add(l);
    }

    /** The Ascent says a stand was taken. */
    public static void standTaken(ServerPlayer p, String trial, String move, BlockPos stand) {
        for (StandListener l : STANDS) l.taken(p, trial, move, stand);
    }

    private static Trials trials = new Trials() {};

    public static void install(Trials t) {
        trials = t;
    }

    /** The installed trials (tests swap in a stand-in and put this back in try/finally). */
    public static Trials installed() {
        return trials;
    }

    public static boolean atEdge(ServerPlayer p, String trial) {
        return trials.atEdge(p, trial);
    }

    public static void done(ServerPlayer p, String trial, int ticks, int medal) {
        trials.done(p, trial, ticks, medal);
    }

    public static void at(ServerPlayer p, String trial, String mark, BlockPos from, BlockPos to) {
        trials.at(p, trial, mark, from, to);
    }

    public static Pay pay(ServerPlayer p, String trial, int ticks, int medal) {
        return trials.pay(p, trial, ticks, medal);
    }

    public static boolean finished(ServerPlayer p, String trial) {
        return trials.finished(p, trial);
    }
}
