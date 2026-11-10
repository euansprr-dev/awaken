package dev.awaken.body.ascent;

import dev.awaken.body.ascent.AscentRules.Kin;
import net.minecraft.util.Mth;

/**
 * The air's contract and the moves it earned (SPEC §62, Rope and Wings): what happens to a body the Ascent holds when
 * something else moves it (a power's push, a blast, a knock, a blink), where the flight steers while you aim, and the
 * rules of the flare, the skim, the takeoff and the rope strike. Plain functions on {@link Kin}, shared by the client's
 * prediction and the tests; nothing here allocates.
 */
public final class AirRules {
    private AirRules() {}

    // ---- a push carries ---------------------------------------------------------------------------------------------

    /** No push takes a body past this speed (blocks a tick) unless it already had more. */
    public static final double PUSH_MAX = 3.0;
    /** Motion that differs by less than this (squared) was not set from outside. */
    public static final double PUSH_EPS = 1e-5;

    /**
     * A push from outside while a state holds the body. Flying free, on wings and in flight it is added to the state's
     * own velocity; on a swinging rope only its part along the swing counts (the rope takes the rest); a zip and a
     * rappel hold the body and take none. {@code k} is the state's point (the hand on a rope), (ax, ay, az) the rope's
     * anchor. True when the velocity changed.
     */
    public static boolean push(Kin k, AscentState st, double px, double py, double pz, double ax, double ay, double az) {
        switch (st) {
            case CARRY, GLIDE, SOAR -> {
            }
            case TETHER -> {
                double rx = ax - k.x, ry = ay - k.y, rz = az - k.z, rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (rl < 1e-6) return false;
                double along = (px * rx + py * ry + pz * rz) / rl;
                px -= rx / rl * along;
                py -= ry / rl * along;
                pz -= rz / rl * along;
            }
            default -> {
                return false;
            }
        }
        if (px * px + py * py + pz * pz < PUSH_EPS) return false;
        double had = k.speed();
        k.vx += px;
        k.vy += py;
        k.vz += pz;
        k.capSpeed(Math.max(had, PUSH_MAX));
        return true;
    }

    /**
     * A cast of your own that sets a motion (a dash, a leap, a launch) while a state holds the body: along the cast's way
     * the body takes the faster of what it had and what the cast gives, and what it had across that way stays. So a
     * dash never slows a dive, and a launch straight up leaves the glide its speed. A push like any other after that.
     */
    public static boolean cast(Kin k, AscentState st, double vx, double vy, double vz, double ax, double ay, double az) {
        double l = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (l < 1e-4) return false;
        double along = (k.vx * vx + k.vy * vy + k.vz * vz) / l;
        double more = l - along;
        if (more <= 0) return false;
        return push(k, st, vx / l * more, vy / l * more, vz / l * more, ax, ay, az);
    }

    /** Levitation in a free state: the game's own rule, on the state's velocity (so a lift lifts open wings too). */
    public static void levitate(Kin k, int amplifier) {
        k.vy += (0.05 * (amplifier + 1) - k.vy) * 0.2;
    }

    // ---- a blink keeps your state -----------------------------------------------------------------------------------

    /** A teleport this soon after a cast of your own (ticks), this near (blocks), is that cast's. */
    public static final int BLINK_TICKS = 20;
    public static final double BLINK_REACH = 16;

    /**
     * Whether a teleport moves the state with the body: only a free state (a rope would be left behind), only a cast
     * the client itself just made, only near. Anything else ends the state, as it always did.
     */
    public static boolean blinkKeeps(AscentState st, int sinceCast, double distSq) {
        return (st == AscentState.CARRY || st == AscentState.GLIDE || st == AscentState.SOAR) && sinceCast >= 0 && sinceCast <= BLINK_TICKS
                && distSq <= (BLINK_REACH + 1) * (BLINK_REACH + 1);
    }

    // ---- the aim hold -----------------------------------------------------------------------------------------------

    /** Letting go of the aim, the flight follows the look again over this many ticks. */
    public static final int AIM_RETURN = 6;

    /**
     * What the flight steers by, into out[0] (yaw) and out[1] (pitch): the line it had when the aim began while the aim
     * is held, the look otherwise, and for {@link #AIM_RETURN} ticks after letting go a blend from one to the other.
     */
    public static void aimSteer(float lookYaw, float lookPitch, float heldYaw, float heldPitch, boolean holding, int sinceRelease, float[] out) {
        if (holding) {
            out[0] = heldYaw;
            out[1] = heldPitch;
        } else if (sinceRelease >= 0 && sinceRelease < AIM_RETURN) {
            float k = (sinceRelease + 1) / (float) AIM_RETURN;
            k = k * k * (3 - 2 * k);
            out[0] = heldYaw + Mth.wrapDegrees(lookYaw - heldYaw) * k;
            out[1] = heldPitch + (lookPitch - heldPitch) * k;
        } else {
            out[0] = lookYaw;
            out[1] = lookPitch;
        }
    }

    /** Tether mastery that opens the spring off a top (the move itself is the client's: AscentMoves.pullOver). */
    public static final int SPRING_LEVEL = 3;

    // ---- the flare --------------------------------------------------------------------------------------------------

    /** Coming down this slow (blocks a tick, all of it and the fall alone) this near the ground (blocks) is a flare. */
    public static final double FLARE_SPEED = 0.95, FLARE_FALL = 0.6, FLARE_HEIGHT = 1.8;

    public static boolean flares(double speed, double vy, double clearance) {
        return vy < 0.02 && speed <= FLARE_SPEED && -vy <= FLARE_FALL && clearance <= FLARE_HEIGHT;
    }

    // ---- the skim ---------------------------------------------------------------------------------------------------

    /** Wings mastery that opens the skim; how near a surface (blocks), how fast (blocks a tick), and the drag left there. */
    public static final int SKIM_LEVEL = 3;
    public static final double SKIM_NEAR = 2.5, SKIM_SPEED = 1.0, SKIM_DRAG = 0.5;

    /** Gliding this near ground, water or a wall this fast: the air under the wings carries more (never all: level flight must still end). */
    public static boolean skims(int wingsLevel, double speed, double clearance) {
        return wingsLevel >= SKIM_LEVEL && speed >= SKIM_SPEED && clearance <= SKIM_NEAR;
    }

    // ---- the takeoff ------------------------------------------------------------------------------------------------

    /** Looking up at least this (degrees), sneaking this long (ticks), then a jump: straight up this far (blocks) over this long, for this much Aether. */
    public static final float TAKEOFF_LOOK = 20;
    public static final int TAKEOFF_SNEAK = 8, TAKEOFF_TICKS = 16;
    public static final double TAKEOFF_RISE = 14, TAKEOFF_AETHER = 0.10;

    public static boolean takeoffReady(float pitch, int sneakTicks, double aether, boolean onGround) {
        return onGround && pitch <= -TAKEOFF_LOOK && sneakTicks >= TAKEOFF_SNEAK && aether >= Math.max(TAKEOFF_AETHER, AscentRules.AETHER_START);
    }

    /**
     * The rise of a takeoff on its {@code age}th tick (0 the first): fast off the ground and easing to nothing at the
     * top, {@link #TAKEOFF_RISE} blocks in all.
     */
    public static double takeoffStep(int age) {
        if (age < 0 || age >= TAKEOFF_TICKS) return 0;
        // the area under a line falling from v0 to 0 over the ticks, taken a tick at a time, is the rise exactly
        double n = TAKEOFF_TICKS;
        return 2 * TAKEOFF_RISE / n * (1 - (age + 0.5) / n);
    }

    // ---- the rope strike --------------------------------------------------------------------------------------------

    /** Tether mastery that opens the strike; no nearer than this (blocks); the blow lands within this of the creature. */
    public static final int STRIKE_LEVEL = 5;
    public static final double STRIKE_MIN = 4, STRIKE_ARRIVE = 2.4;
    /** The pull: blocks a tick² and the most it reaches; a strike that hasn't landed by this many ticks lets go. */
    public static final double STRIKE_PULL = 0.2, STRIKE_SPEED = 1.7;
    public static final int STRIKE_TICKS = 50, STRIKE_COOLDOWN = 16;
    /** Coming off the blow: up, and this share of the speed kept along the way you came. */
    public static final double STRIKE_UP = 0.62, STRIKE_KEEP = 0.22;
    /** The blow: the hand's own damage and this much more a block a tick of speed, no more than the cap. */
    public static final double STRIKE_PER_SPEED = 2.5, STRIKE_BONUS_MAX = 6;

    public static boolean strikes(int tetherLevel, double dist, double reach) {
        return tetherLevel >= STRIKE_LEVEL && dist > STRIKE_MIN && dist <= reach;
    }

    /** One tick of the pull toward (tx, ty, tz); true when the body is at the creature. */
    public static boolean strikeStep(Kin k, double tx, double ty, double tz) {
        double dx = tx - k.x, dy = ty - k.y, dz = tz - k.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= STRIKE_ARRIVE) return true;
        // the rope draws straight in: what the body had across the line bleeds off, the pull builds along it
        double along = (k.vx * dx + k.vy * dy + k.vz * dz) / d;
        double sx = k.vx - dx / d * along, sy = k.vy - dy / d * along, sz = k.vz - dz / d * along;
        along = Math.min(STRIKE_SPEED, Math.max(along, 0.4) + STRIKE_PULL);
        k.vx = dx / d * along + sx * 0.7;
        k.vy = dy / d * along + sy * 0.7;
        k.vz = dz / d * along + sz * 0.7;
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
        return false;
    }

    /** Off the blow: up, with a little of the way you came. */
    public static void strikeOff(Kin k) {
        k.vx *= STRIKE_KEEP;
        k.vz *= STRIKE_KEEP;
        k.vy = STRIKE_UP;
    }

    public static float strikeDamage(double hand, double speed) {
        return (float) (hand + Math.min(STRIKE_BONUS_MAX, STRIKE_PER_SPEED * Math.max(0, speed)));
    }
}
