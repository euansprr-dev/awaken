package dev.awaken.body;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Awakened Body's numbers (SPEC §13 "Awakened Body") and the plain functions both sides run: where a ledge is,
 * how fast a slide goes at each tick, how much a moving hit adds, how a roll softens a fall. Nothing here touches a
 * player, so GameTests call it directly and the client predicts with exactly what the server checks.
 */
public final class BodyRules {
    private BodyRules() {}

    // ---- soft landing (awakening) -----------------------------------------------------------------------------------
    /** Falls shorter than this (vanilla's own safe fall) never roll. */
    public static final double ROLL_MIN_FALL = 3.0;
    /** The roll adds this much safe fall... */
    public static final double ROLL_SAFE_EXTRA = 3.0;
    /** ...and beyond it the fall hurts this much. */
    public static final float ROLL_DAMAGE = 0.6F;
    public static final int ROLL_TICKS = 10;

    // ---- tactical sprint (awakening) --------------------------------------------------------------------------------
    /** Movement speed ×(1 + this) on top of the sprint (attribute modifier, total multiplier). */
    public static final double TAC_SPEED = 0.25;
    public static final int TAC_TICKS = 60;
    /** Ticks after it ends before it can run again (rank E; {@link #tacRecharge} shortens it by rank). */
    public static final int TAC_RECHARGE = 80;

    /** Ranks improve the moves (SPEC §16.3): the tactical sprint recharges 10 ticks sooner a rank, down to 30. */
    public static int tacRecharge(dev.awaken.progression.Rank rank) {
        return tacRecharge(rank, 0);
    }

    /**
     * With a recharge perk ({@link #SPRINT_RECHARGE_PERK}: a fraction, the Living Wilds' Courier's Knot gives 0.25):
     * that much less of the rank's recharge, at most half, and never under the S rank's 30. Worth most early.
     */
    public static int tacRecharge(dev.awaken.progression.Rank rank, double perk) {
        int base = Math.max(30, TAC_RECHARGE - 10 * rank.ordinal());
        return Math.max(30, (int) Math.round(base * (1 - Math.max(0, Math.min(0.5, perk)))));
    }

    /** A perk that recharges the tactical sprint faster (Progression.perk: a fraction, additive across sources). */
    public static final String SPRINT_RECHARGE_PERK = "awaken:sprint_recharge";

    /** The recharge for this player now: their rank and their perk (both sides read the same). */
    public static int tacRecharge(net.minecraft.world.entity.player.Player p) {
        return tacRecharge(Body.runnerRank(p), dev.awaken.progression.Progression.perk(p, SPRINT_RECHARGE_PERK));
    }

    /** The slide's cooldown, 2 ticks shorter a rank, down to 10. */
    public static int slideCooldown(dev.awaken.progression.Rank rank) {
        return Math.max(10, SLIDE_COOLDOWN - 2 * rank.ordinal());
    }
    /** The second tap of the sprint key must come within this many ticks of the first. */
    public static final int TAC_DOUBLE_TAP = 7;
    public static final float TAC_EXHAUSTION = 0.4F;
    public static final Identifier TAC_ID = Awaken.id("body_tactical_sprint");
    /** Applied on the server (synced) and predicted on the client with the same id. */
    public static final AttributeModifier TAC_MODIFIER = new AttributeModifier(TAC_ID, TAC_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    // ---- slide (rank D) ---------------------------------------------------------------------------------------------
    public static final int SLIDE_TICKS = 14;
    /** Entry speed (blocks per tick) from a run, and from a tactical sprint. */
    public static final double SLIDE_V0 = 0.38, SLIDE_V0_TACTICAL = 0.46;
    /** The slide sheds this share of its speed by its last tick. */
    public static final double SLIDE_FADE = 0.5;
    public static final int SLIDE_COOLDOWN = 20;
    public static final float SLIDE_EXHAUSTION = 0.1F;
    /** Horizontal speed a jump out of the slide adds (the slide-cancel). */
    public static final double SLIDE_JUMP = 0.15;
    /** The server lets a slide run this many ticks past the client's before ending it itself. */
    public static final int SLIDE_GRACE = 6;

    // ---- stair slide (SPEC §17.22) ----------------------------------------------------------------------------------
    /** On a run the slide's speed tends to this (b/t): over a tactical sprint's ~0.35, near the slide's own entry. */
    public static final double STAIR_SPEED = 0.42;
    /** ...by at most this a tick: up from a faded slide, down from a tactical one. */
    public static final double STAIR_EASE = 0.012;
    /** The run's fall line must be within 35° of the slide's way. */
    public static final double STAIR_CONE_COS = 0.819;
    /** The way bends onto the fall line at most this much a tick (degrees). */
    public static final double STAIR_BEND = 3.0;
    /** At the bottom the slide's last this-many ticks (its own decay) carry the body out. */
    public static final int STAIR_RUNOUT = 8;
    /** The longest a run keeps one slide going (6 s), then the run-out. */
    public static final int STAIR_MAX_TICKS = 120;
    /** The most a tick lifts a body onto the run's line (one that had dropped off a step before the slide began). */
    static final double STAIR_LIFT = 0.25;
    /** A step of a run: a stair's or slab's half block (a full block or more is an edge, not a stair). */
    static final double STAIR_STEP_MIN = 0.25, STAIR_STEP_MAX = 0.6;

    // ---- mantle (rank C) --------------------------------------------------------------------------------------------
    /** A ledge counts from just above a plain block step (vanilla jumps those) up to this above the feet. */
    public static final double MANTLE_MIN = 1.1, MANTLE_MAX = 1.6;
    public static final int MANTLE_TICKS = 6;
    /** Free space needed over the ledge. */
    public static final double MANTLE_HEADROOM = 2.0;
    /** How far in front of the body the ledge's face may be: the client, and the server (it may be a tick behind). */
    public static final double MANTLE_REACH = 0.6, MANTLE_REACH_SERVER = 1.1;
    public static final float MANTLE_EXHAUSTION = 0.05F;

    // ---- momentum hits and trips ------------------------------------------------------------------------------------
    /** Horizontal speed (blocks per tick) where a melee hit starts gaining, and the span to the full bonus. */
    public static final double MOMENTUM_FROM = 0.2, MOMENTUM_SPAN = 0.25;
    /** The most a moving hit adds: +30% damage and this much extra knockback strength. */
    public static final double MOMENTUM_MAX = 0.3, MOMENTUM_KNOCK = 0.6;
    /** Below this attack strength (vanilla's cooldown) a swing gets nothing extra. */
    public static final float MOMENTUM_MIN_STRENGTH = 0.9F;
    /** A slide trips hostile creatures no taller than this; they stagger for this long. */
    public static final double TRIP_MAX_HEIGHT = 1.0;
    public static final int TRIP_TICKS = 16;

    /** Player box (standing). */
    static final double HALF_WIDTH = 0.3, HEIGHT = 1.8;

    // ---- slide ------------------------------------------------------------------------------------------------------

    /** Horizontal speed on slide tick {@code t} (0-based) for an entry speed of v0. */
    public static double slideSpeed(int t, double v0) {
        return v0 * (1 - SLIDE_FADE * Mth.clamp(t, 0, SLIDE_TICKS) / (double) SLIDE_TICKS);
    }

    /** The whole distance an unobstructed slide covers. */
    public static double slideDistance(double v0) {
        double d = 0;
        for (int t = 0; t < SLIDE_TICKS; t++) d += slideSpeed(t, v0);
        return d;
    }

    /** Entry speed of a slide that starts at {@code speed}: never below the slide's own, so it always "keeps speed". */
    public static double slideEntry(double speed, boolean fromTactical) {
        return Math.max(fromTactical ? SLIDE_V0_TACTICAL : SLIDE_V0, Math.min(speed, 0.9));
    }

    // ---- stair slide ------------------------------------------------------------------------------------------------

    /**
     * The ground along a slide's way, read once a tick (SPEC §17.22): collision tops on the line through the body's
     * centre every 1/16 block, from {@link #BACK} behind the box's rear to {@link #AHEAD} past its centre, and the noses
     * where that line drops a stair's or slab's half step. Each side's thread owns one, so nothing is allocated a tick.
     */
    public static final class StairProfile {
        static final double SPACING = 1 / 16.0, BACK = 1.2, AHEAD = 1.6;
        static final int N = (int) Math.round((BACK + HALF_WIDTH + AHEAD) / SPACING);
        /** The sample under the box's rear edge. */
        static final int REAR = (int) Math.floor(BACK / SPACING);
        final double[] top = new double[N];
        final int[] nose = new int[N];
        int noses;
        /** The ride this tick was the run's last: the feet are on the floor below its last step. */
        public boolean last;
        private final double[] tmp = new double[2];
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        /** Where and which way it was read (the noses are found again exactly from these). */
        private double x, y, z, dx, dz;

        /** Distance along the way from the body's centre of sample i, and of the nose after sample i. */
        static double q(int i) {
            return -HALF_WIDTH - BACK + (i + 0.5) * SPACING;
        }

        static double noseAt(int i) {
            return q(i) + SPACING / 2;
        }
    }

    /** Reads the profile for feet at (x, y, z) going (dx, dz). Behind the rear an edge or a rise only forgets the noses before it. */
    static void readStairs(BlockGetter level, CollisionContext ctx, StairProfile sp, double x, double y, double z, double dx, double dz) {
        sp.x = x;
        sp.y = y;
        sp.z = z;
        sp.dx = dx;
        sp.dz = dz;
        for (int i = 0; i < StairProfile.N; i++) {
            double q = StairProfile.q(i), px = x + dx * q, pz = z + dz * q;
            // behind the rear the run climbs back up (a block a block at most, and a step); ahead nothing is over the feet
            double hi = q < -HALF_WIDTH ? y + 0.55 + (-HALF_WIDTH - q) * 1.05 : y + 0.3;
            surface(level, ctx, sp.m, px - 0.01, pz - 0.01, px + 0.01, pz + 0.01, y - 2.5, hi, sp.tmp);
            sp.top[i] = sp.tmp[0];
        }
        sp.noses = 0;
        for (int i = 0; i + 1 < StairProfile.N; i++) {
            boolean ahead = StairProfile.q(i + 1) >= -HALF_WIDTH;
            double a = sp.top[i], b = sp.top[i + 1], d = a - b;
            boolean step = !Double.isNaN(d) && d >= STAIR_STEP_MIN && d <= STAIR_STEP_MAX
                    && (sp.noses == 0 || sp.nose[sp.noses - 1] < i - 1); // two drops in a row are one edge
            boolean flat = !Double.isNaN(d) && d < STAIR_STEP_MIN && d > -0.05;
            if (step) sp.nose[sp.noses++] = i;
            else if (!flat) { // a gap, an edge or a rise: the run ends here (ahead), or starts after it (behind)
                if (sp.noses > 0 && sp.nose[sp.noses - 1] == i - 1) sp.noses--; // that drop was the edge's first half
                if (ahead) return;
                sp.noses = 0;
            }
        }
    }

    /**
     * Where the feet ride on the profile just read: on the straight line from nose to nose (never a bump per step),
     * flat before the first, down the last step at the slope of the ones before it onto the floor. NaN when no run is
     * under the body. Sets {@link StairProfile#last} when this ride puts the feet on the floor below the last step.
     */
    static double ride(BlockGetter level, CollisionContext ctx, StairProfile sp) {
        sp.last = false;
        double r = -HALF_WIDTH;
        if (sp.noses == 0 || Double.isNaN(sp.top[StairProfile.REAR])) return Double.NaN;
        // b: the first nose ahead of the rear, found exactly (the grid can put one a hair on the wrong side)
        int b = 0;
        while (b < sp.noses && StairProfile.noseAt(sp.nose[b]) <= r) b++;
        double xb = b < sp.noses ? edge(level, ctx, sp, sp.nose[b]) : Double.NaN;
        if (b < sp.noses && xb <= r) xb = ++b < sp.noses ? edge(level, ctx, sp, sp.nose[b]) : Double.NaN;
        int a = b - 1;
        double xa = a >= 0 ? edge(level, ctx, sp, sp.nose[a]) : Double.NaN;
        if (a >= 0 && xa > r) { // the rear is still on the step before it
            xb = xa;
            b = a--;
            xa = a >= 0 ? edge(level, ctx, sp, sp.nose[a]) : Double.NaN;
        }
        if (a < 0) return b < sp.noses ? sp.top[sp.nose[b]] : Double.NaN; // on the top, the first step still ahead
        double ha = sp.top[sp.nose[a]];
        if (b < sp.noses) {
            double hb = sp.top[sp.nose[b]];
            return ha + (hb - ha) * (r - xa) / Math.max(1e-3, xb - xa);
        }
        // past the last nose: down the last step as steep as the run was (a lone step: a stair's), onto the floor
        double slope = 1.0, floor = sp.top[sp.nose[a] + 1];
        if (a > 0) slope = (sp.top[sp.nose[a - 1]] - ha) / Math.max(1e-3, xa - edge(level, ctx, sp, sp.nose[a - 1]));
        double line = ha - slope * (r - xa);
        if (line <= floor + 1e-3) {
            sp.last = true;
            return floor;
        }
        return line;
    }

    /** Where the step after sample i really ends (to 1/512 block): a nose found on the grid wobbles as the body moves. */
    private static double edge(BlockGetter level, CollisionContext ctx, StairProfile sp, int i) {
        double lo = StairProfile.q(i), hi = StairProfile.q(i + 1), high = sp.top[i];
        for (int k = 0; k < 5; k++) {
            double mid = (lo + hi) / 2, px = sp.x + sp.dx * mid, pz = sp.z + sp.dz * mid;
            double top = pointTopRaw(level, ctx, sp, px, pz, high - 1.0, high + 0.05);
            if (!Double.isNaN(top) && top >= high - 0.01) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    /**
     * Whether a slide with feet at (x, y, z) going (dx, dz) has reached a run: two steps down ahead (a single step is
     * just a step), the run's fall line within 35° of the way. {@code fall} gets the fall line (a unit, horizontal).
     */
    public static boolean stairStart(BlockGetter level, CollisionContext ctx, StairProfile sp, double x, double y, double z, double dx, double dz, double[] fall) {
        // the fall line: the side the ground drops most to (stairs and slab runs are laid along the grid)
        double here = pointTop(level, ctx, sp, x, z, y);
        if (Double.isNaN(here)) return false;
        double best = 0.4, fx = 0, fz = 0;
        for (int k = 0; k < 4; k++) {
            double kx = k == 0 ? 1 : k == 1 ? -1 : 0, kz = k == 2 ? 1 : k == 3 ? -1 : 0;
            double t = pointTop(level, ctx, sp, x + kx * 1.25, z + kz * 1.25, y);
            if (!Double.isNaN(t) && here - t > best + 1e-6) {
                best = here - t;
                fx = kx;
                fz = kz;
            }
        }
        if (fx * dx + fz * dz < STAIR_CONE_COS) return false;
        readStairs(level, ctx, sp, x, y, z, dx, dz);
        int ahead = 0;
        for (int k = 0; k < sp.noses; k++) if (StairProfile.noseAt(sp.nose[k]) > -HALF_WIDTH) ahead++;
        if (ahead < 2 || Double.isNaN(ride(level, ctx, sp))) return false;
        fall[0] = fx;
        fall[1] = fz;
        return true;
    }

    /** Whether the run still goes on under a stair slide at feet (x, y, z) going (dx, dz) (the server's check). */
    public static boolean stairGoesOn(BlockGetter level, CollisionContext ctx, StairProfile sp, double x, double y, double z, double dx, double dz) {
        readStairs(level, ctx, sp, x, y, z, dx, dz);
        return !Double.isNaN(ride(level, ctx, sp)) && !sp.last;
    }

    /**
     * One tick of a stair slide from feet (x, y, z) along (dx, dz) at {@code speed} (horizontal, blocks per tick):
     * {@code out} = the next feet, the way first and then down onto the line (so a step's edge never holds the body up
     * for a tick). False when the run is over here or something is in the way: the slide's run-out takes over. After
     * the ride onto the floor below the last step {@link StairProfile#last} is set: that tick ends the stair part too.
     */
    public static boolean stairStep(BlockGetter level, CollisionContext ctx, StairProfile sp, double x, double y, double z,
                                    double dx, double dz, double speed, double[] out) {
        double nx = x + dx * speed, nz = z + dz * speed;
        if (!crawlFits(level, ctx, nx, y, nz)) return false;
        readStairs(level, ctx, sp, nx, y, nz, dx, dz);
        double ty = ride(level, ctx, sp);
        if (Double.isNaN(ty) || ty > y + 0.6 || ty < y - 0.9) return false;
        out[0] = nx;
        out[1] = Math.min(ty, y + STAIR_LIFT); // a body that dropped off a step (a run's start) eases up onto the line
        out[2] = nz;
        return true;
    }

    /** The next stair-slide speed: toward {@link #STAIR_SPEED}, at most {@link #STAIR_EASE} a tick. */
    public static double stairSpeed(double speed) {
        return speed + Mth.clamp(STAIR_SPEED - speed, -STAIR_EASE, STAIR_EASE);
    }

    /** Turns the unit way {@code d} = {x, z} toward the unit (tx, tz) by at most {@link #STAIR_BEND}° (in place). */
    public static void stairBend(double[] d, double tx, double tz) {
        double angle = Math.atan2(d[0] * tz - d[1] * tx, d[0] * tx + d[1] * tz);
        double turn = Mth.clamp(angle, -STAIR_BEND * Mth.DEG_TO_RAD, STAIR_BEND * Mth.DEG_TO_RAD);
        double c = Math.cos(turn), s = Math.sin(turn), x = d[0], z = d[1];
        d[0] = x * c - z * s;
        d[1] = x * s + z * c;
    }

    private static double pointTop(BlockGetter level, CollisionContext ctx, StairProfile sp, double x, double z, double y) {
        return pointTopRaw(level, ctx, sp, x, z, y - 2.5, y + 0.3);
    }

    private static double pointTopRaw(BlockGetter level, CollisionContext ctx, StairProfile sp, double x, double z, double yLo, double yHi) {
        surface(level, ctx, sp.m, x - 0.01, z - 0.01, x + 0.01, z + 0.01, yLo, yHi, sp.tmp);
        return sp.tmp[0];
    }

    // ---- momentum ---------------------------------------------------------------------------------------------------

    /** Extra damage share of a full-strength melee hit at this horizontal speed (0 at a walk, 0.3 at most). */
    public static double momentum(double speed) {
        return Mth.clamp((speed - MOMENTUM_FROM) / MOMENTUM_SPAN, 0, 1) * MOMENTUM_MAX;
    }

    // ---- soft landing -----------------------------------------------------------------------------------------------

    /**
     * How a roll changes a fall, or null when it doesn't apply. {@code otherSafe} is the safe fall another rule already
     * gives (the attribute above its base: gate Low Gravity, jet boots); {@code perkFactor} the damage factor a perk
     * already applied. The kinder rule wins; they never add up. Returns {distance, multiplier factor}.
     */
    public static double @Nullable [] roll(double distance, double otherSafe, double perkFactor) {
        if (distance < ROLL_MIN_FALL) return null;
        double extra = ROLL_SAFE_EXTRA - Math.max(0, otherSafe);
        if (extra <= 0) return null; // another rule is already at least as kind
        double factor = perkFactor <= ROLL_DAMAGE ? 1.0 : ROLL_DAMAGE / Math.max(1e-6, perkFactor);
        return new double[]{Math.max(0, distance - extra), factor};
    }

    /** Vanilla's fall damage for a distance with the given safe fall and multiplier (LivingEntity.calculateFallDamage). */
    public static int fallDamage(double distance, double safe, double multiplier) {
        return Mth.floor((distance + 1.0E-6 - safe) * multiplier);
    }

    // ---- mantle -----------------------------------------------------------------------------------------------------

    /** A ledge in front of the player: where the feet go ({@code x, y, z}), its height above the feet and the face's distance. */
    public record Ledge(double x, double y, double z, double height, double face) {}

    /**
     * The ledge a player standing with feet at (px, py, pz) and facing {@code yaw} can mantle onto, or null. Checks:
     * something stands in front within {@code reach}; its top is between {@link #MANTLE_MIN} and {@link #MANTLE_MAX}
     * above the feet and is not a fence, wall or gate (collision taller than its block); {@link #MANTLE_HEADROOM} is free
     * over it; nothing is over the player's head on the way up; and the way over the lip is clear.
     */
    public static @Nullable Ledge findLedge(BlockGetter level, CollisionContext ctx, double px, double py, double pz, float yaw, double reach) {
        double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double w = HALF_WIDTH;
        // 1. the face: the first step forward where the lower body would run into something
        double face = -1;
        for (double s = 0.05; s <= reach + 1e-6; s += 0.05) {
            double cx = px + dx * s, cz = pz + dz * s;
            if (blocked(level, ctx, m, cx - w, py + 0.05, cz - w, cx + w, py + MANTLE_MAX, cz + w)) {
                face = s;
                break;
            }
        }
        if (face < 0) return null;
        // 2. the feet's new place: a body width past the face, on whatever top is there
        double f = face + 2 * w + 0.05;
        double tx = px + dx * f, tz = pz + dz * f;
        double[] top = {Double.NaN, 0};
        surface(level, ctx, m, tx - w + 0.02, tz - w + 0.02, tx + w - 0.02, tz + w - 0.02, py + 0.01, py + MANTLE_MAX + 0.01, top);
        if (Double.isNaN(top[0])) return null;
        double h = top[0] - py;
        if (h < MANTLE_MIN - 1e-6 || h > MANTLE_MAX + 1e-6) return null;
        if (top[1] != 0) return null; // a fence, wall or gate top: vanilla's "you can't jump this" stays true
        double ty = top[0];
        // 3. room to stand up there
        if (blocked(level, ctx, m, tx - w + 0.01, ty + 1e-3, tz - w + 0.01, tx + w - 0.01, ty + MANTLE_HEADROOM, tz + w - 0.01)) return null;
        // 4. the way up: nothing over the head from here to standing height on the ledge
        if (blocked(level, ctx, m, px - w + 0.01, py + 0.05, pz - w + 0.01, px + w - 0.01, ty + HEIGHT, pz + w - 0.01)) return null;
        // 5. the way over the lip at ledge height (a box spanning start and end: conservative on diagonals)
        double x0 = Math.min(px, tx) - w + 0.01, x1 = Math.max(px, tx) + w - 0.01;
        double z0 = Math.min(pz, tz) - w + 0.01, z1 = Math.max(pz, tz) + w - 0.01;
        if (blocked(level, ctx, m, x0, ty + 1e-3, z0, x1, ty + HEIGHT, z1)) return null;
        return new Ledge(tx, ty, tz, h, face);
    }

    /** The feet's place on mantle tick k (1..{@link #MANTLE_TICKS}) from start s to ledge l: up first, then over. */
    public static void mantlePath(double sx, double sy, double sz, Ledge l, int k, double[] out) {
        double u = Mth.clamp(k / (double) MANTLE_TICKS, 0, 1);
        double rise = Math.min(1, u / 0.55);
        rise = 1 - (1 - rise) * (1 - rise); // fast at first, easing onto the lip
        double lift = l.height() + (k < MANTLE_TICKS ? 0.04 : 0);
        double over = u < 0.5 ? 0.15 * (u / 0.5) : 0.15 + 0.85 * ((u - 0.5) / 0.5);
        double fx = l.x() - sx, fz = l.z() - sz;
        double flen = Math.sqrt(fx * fx + fz * fz);
        // until the feet clear the lip, stay off the face
        double maxFwd = rise < 0.97 ? Math.max(0, l.face() - 0.04) : flen;
        double fwd = Math.min(over * flen, maxFwd);
        double nx = flen < 1e-6 ? 0 : fx / flen, nz = flen < 1e-6 ? 0 : fz / flen;
        out[0] = sx + nx * fwd;
        out[1] = sy + lift * rise;
        out[2] = sz + nz * fwd;
    }

    // ---- block collision without a level's entity search -----------------------------------------------------------

    /** Whether any block's collision overlaps the box (blocks only: entities and the border don't count). */
    public static boolean blocked(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                                  double x0, double y0, double z0, double x1, double y1, double z1) {
        int bx0 = Mth.floor(x0), bx1 = Mth.floor(x1 - 1e-7);
        int by0 = Mth.floor(y0) - 1, by1 = Mth.floor(y1 - 1e-7); // one below: fences and walls reach up into the next block
        int bz0 = Mth.floor(z0), bz1 = Mth.floor(z1 - 1e-7);
        for (int bx = bx0; bx <= bx1; bx++) for (int bz = bz0; bz <= bz1; bz++) for (int by = by0; by <= by1; by++) {
            BlockState s = level.getBlockState(m.set(bx, by, bz));
            if (s.isAir()) continue;
            VoxelShape shape = s.getCollisionShape(level, m, ctx);
            if (shape.isEmpty()) continue;
            if (shape.max(net.minecraft.core.Direction.Axis.Y) + by <= y0 || shape.min(net.minecraft.core.Direction.Axis.Y) + by >= y1) continue;
            List<AABB> boxes = shape.toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB b = boxes.get(i);
                if (b.maxX + bx > x0 && b.minX + bx < x1 && b.maxY + by > y0 && b.minY + by < y1 && b.maxZ + bz > z0 && b.minZ + bz < z1) return true;
            }
        }
        return false;
    }

    /**
     * The highest collision top under a footprint between yLo and yHi: out[0] = that top (NaN if none or if something
     * reaches above yHi), out[1] = 1 when the top belongs to a collision taller than its block (fence, wall, gate).
     */
    static void surface(BlockGetter level, CollisionContext ctx, BlockPos.MutableBlockPos m,
                        double x0, double z0, double x1, double z1, double yLo, double yHi, double[] out) {
        double best = Double.NaN;
        boolean tall = false;
        int bx0 = Mth.floor(x0), bx1 = Mth.floor(x1 - 1e-7), bz0 = Mth.floor(z0), bz1 = Mth.floor(z1 - 1e-7);
        int by0 = Mth.floor(yLo) - 1, by1 = Mth.floor(yHi);
        for (int bx = bx0; bx <= bx1; bx++) for (int bz = bz0; bz <= bz1; bz++) for (int by = by0; by <= by1; by++) {
            BlockState s = level.getBlockState(m.set(bx, by, bz));
            if (s.isAir()) continue;
            VoxelShape shape = s.getCollisionShape(level, m, ctx);
            if (shape.isEmpty()) continue;
            List<AABB> boxes = shape.toAabbs();
            for (int i = 0; i < boxes.size(); i++) {
                AABB b = boxes.get(i);
                if (!(b.maxX + bx > x0 && b.minX + bx < x1 && b.maxZ + bz > z0 && b.minZ + bz < z1)) continue;
                double top = b.maxY + by;
                if (top <= yLo) continue;
                if (top > yHi) {
                    out[0] = Double.NaN;
                    return;
                }
                if (Double.isNaN(best) || top > best) {
                    best = top;
                    tall = shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0 + 1e-6;
                } else if (Math.abs(top - best) < 1e-6 && shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0 + 1e-6) {
                    tall = true;
                }
            }
        }
        out[0] = best;
        out[1] = tall ? 1 : 0;
    }

    /** Whether a crawl-height body (the slide) fits with its feet here. */
    public static boolean crawlFits(BlockGetter level, CollisionContext ctx, double x, double y, double z) {
        return !blocked(level, ctx, new BlockPos.MutableBlockPos(), x - HALF_WIDTH + 1e-4, y + 1e-4, z - HALF_WIDTH + 1e-4,
                x + HALF_WIDTH - 1e-4, y + 0.6 - 1e-4, z + HALF_WIDTH - 1e-4);
    }
}
