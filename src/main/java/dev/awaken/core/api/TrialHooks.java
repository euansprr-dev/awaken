package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

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

    public interface Trials {
        /**
         * A runner reached the trial's edge (the chasm). Whether they now hold what crosses it: the trial gives it here,
         * the first time, with its moment.
         */
        default boolean atEdge(ServerPlayer p, String trial) { return false; }

        /** A runner crossed and reached the far landing: the trial is done ({@code ticks} on the clock, medal 1..3). */
        default void done(ServerPlayer p, String trial, int ticks, int medal) {}
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
}
