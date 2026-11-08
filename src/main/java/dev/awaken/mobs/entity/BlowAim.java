package dev.awaken.mobs.entity;

import dev.awaken.mobs.boss.KitMoves;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aims a kit blow (SPEC §47.15). A swing is drawn from its clip, and most clips were made without anyone standing in
 * front of them: their blades cross the aim line over a player's head or pull up short of them. At the blow's lock the
 * body takes the smallest correction that brings its sweep onto the target: a turn, a step, a lean, for a flier a dip,
 * for a lunge a longer or shorter run. Past those limits there is no blow to begin, and the body closes first.
 */
final class BlowAim {
    private BlowAim() {}

    /** The corrections a body may take, in degrees and blocks; each list starts with "none". */
    private static final float[] TURNS = {0, 25, -25, 50, -50};
    private static final float[] STEPS = {0, 0.6F, -0.6F, 1.2F, -1.2F};
    private static final float[] RUNS = {1F, 0.75F, 0.5F, 0.35F, 1.25F, 1.5F, 1.75F, 2F};
    private static final float[] LEANS = {0, 8, -8, 16, -16, 24};
    private static final float[] DIPS = {0, -0.5F, 0.5F, -1F, 1F, -1.5F, 1.5F};
    /** How much of the box a blow must reach into: a blow that only just touches misses after one tick's drift. */
    private static final float MARGIN = 0.08F;

    private static final int[][] WALK_STEP = table(STEPS.length, 1), FLY_STEP = table(STEPS.length, DIPS.length), WALK_RUN = runs(1), FLY_RUN = runs(DIPS.length);

    /** The correction chosen. */
    static final class Result {
        float turn, step, run = 1F, lean, dip;

        void clear() {
            turn = step = lean = dip = 0;
            run = 1F;
        }
    }

    private static int[][] table(int moves, int dips) {
        return sorted(moves, dips, false);
    }

    private static int[][] runs(int dips) {
        return sorted(RUNS.length, dips, true);
    }

    /** Every correction, cheapest first: a turn is cheap, a lean less so, a step or a dip the most. */
    private static int[][] sorted(int moves, int dips, boolean run) {
        List<int[]> all = new ArrayList<>();
        for (int t = 0; t < TURNS.length; t++) for (int m = 0; m < moves; m++) for (int l = 0; l < LEANS.length; l++) for (int d = 0; d < dips; d++) all.add(new int[]{t, m, l, d});
        all.sort(Comparator.comparingDouble(c -> Math.abs(TURNS[c[0]]) / 30F + (run ? Math.abs(RUNS[c[1]] - 1F) * 1.5F : Math.abs(STEPS[c[1]])) + Math.abs(LEANS[c[2]]) / 14F + Math.abs(DIPS[c[3]])));
        return all.toArray(new int[0][]);
    }

    /**
     * Finds the cheapest correction that lands {@code m} on a target standing at (lx, lz) in the body's frame (+z the way
     * it would face), its box from ly0 to ly1 over the body's feet and {@code radius} wide; a flier dips no lower than
     * {@code lowest} (the floor under it), and it steps forward at most {@code room}. False when none does.
     */
    static boolean solve(KitMoves.Move m, float hip, double lx, double lz, double ly0, double ly1, double radius, boolean flies, float lowest, boolean canTurn, double room, Result out) {
        int active = Math.max(1, m.time().active());
        float advance = m.motion().advance();
        int[][] order = advance != 0 ? (flies ? FLY_RUN : WALK_RUN) : (flies ? FLY_STEP : WALK_STEP);
        double centre = Math.max(0, Math.sqrt(lx * lx + lz * lz) - 0.1);
        for (int[] c : order) {
            if ((!canTurn && c[0] != 0) || (flies && DIPS[c[3]] < lowest)) continue;
            float turn = TURNS[c[0]], step = advance != 0 ? 0 : STEPS[c[1]], run = advance != 0 ? RUNS[c[1]] : 1F, lean = LEANS[c[2]], dip = flies ? DIPS[c[3]] : 0;
            if (step > room || advance * run > centre) continue; // a step stops short of you, a lunge at you, never through
            if (lands(m, hip, lx, lz, ly0, ly1, radius, active, advance * run, turn, step, lean, dip)) {
                out.turn = turn;
                out.step = step;
                out.run = run;
                out.lean = lean;
                out.dip = dip;
                return true;
            }
        }
        return false;
    }

    /** Whether the blow lands with no correction at all. */
    static boolean bare(KitMoves.Move m, float hip, double lx, double lz, double ly0, double ly1, double radius) {
        return lands(m, hip, lx, lz, ly0, ly1, radius, Math.max(1, m.time().active()), m.motion().advance(), 0, 0, 0, 0);
    }

    private static boolean lands(KitMoves.Move m, float hip, double lx, double lz, double ly0, double ly1, double radius, int active, float advance,
                                 float turn, float step, float lean, float dip) {
        float co = Mth.cos(turn * Mth.DEG_TO_RAD), so = Mth.sin(turn * Mth.DEG_TO_RAD), cl = Mth.cos(lean * Mth.DEG_TO_RAD), sl = Mth.sin(lean * Mth.DEG_TO_RAD);
        List<List<KitMoves.Capsule>> frames = m.hits();
        double r0 = ly0 + MARGIN, r1 = ly1 - MARGIN, rad = Math.max(0.05, radius - MARGIN);
        for (int k = 0; k < frames.size(); k++) {
            float along = advance * (k + 1) / active;
            for (KitMoves.Capsule c : frames.get(k)) {
                float ax = c.bx() - c.ax(), ay = c.by() - c.ay(), az = c.bz() - c.az();
                int n = Math.max(1, Mth.ceil(Mth.sqrt(ax * ax + ay * ay + az * az) / 0.5F));
                for (int i = 0; i <= n; i++) {
                    float f = i / (float) n;
                    float y = c.ay() + ay * f - hip, z = c.az() + az * f + along;
                    float py = hip + y * cl - z * sl + dip, pz = y * sl + z * cl, x = c.ax() + ax * f;
                    double wx = x * co - pz * so, wz = x * so + pz * co + step;
                    double h = Math.max(0, Math.sqrt((wx - lx) * (wx - lx) + (wz - lz) * (wz - lz)) - rad);
                    double v = Math.max(0, Math.max(r0 - py, py - r1));
                    if (h * h + v * v <= c.r() * c.r()) return true;
                }
            }
        }
        return false;
    }
}
