package dev.awaken.body.ascent;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a rope bites. The look's own ray wins when it hits something in reach; otherwise the rope finds the best anchor
 * near the aim, higher and ahead (the way swinging games let you hold the button and keep going without aiming at
 * every trunk): a small fan of rays above and around the look, each hit scored by how well it would swing you on.
 * Runs once per press (a dozen rays at most). Both sides: the client picks, the server will check the same block.
 */
public final class AscentAnchors {
    private AscentAnchors() {}

    /** An anchor: the point, the face's normal (the rope's end sits a hair off the face), and whether it was aimed. */
    public record Anchor(double x, double y, double z, boolean aimed) {}

    /** A direct hit at least this far above the eye is a swing anchor as aimed. */
    public static final double AIMED_RISE = 6;
    /** A zip needs its anchor at least this far away (closer is a wall in your face: look for a swing instead). */
    public static final double ZIP_MIN = 4;

    /** The assist's rays: angles above the horizon, and yaw offsets either side of the look. */
    private static final float[] FAN_UP = {20, 30, 40, 50, 62};
    private static final float[] FAN_YAW = {0, -14, 14, -28, 28};

    /**
     * The anchor a press would bite. The look's own ray wins when it hits high (6+ above the eye: you aimed at
     * something to swing from) or low and at least 4 away (a ledge or a trunk to pull yourself to: a zip). Anything
     * else (sky, a trunk only a few blocks up, a wall in your face) asks the assist: rays climbing 20-62° above the
     * horizon, straight ahead and up to 28° either side, scored for a good swing (about 14 up and 13 ahead). Only when
     * that finds nothing does a low direct hit swing as it is.
     */
    public static @Nullable Anchor find(Level level, Entity who, Vec3 eye, float yaw, float pitch, double range) {
        BlockHitResult hit = ray(level, who, eye, yaw, pitch, range);
        Anchor fallback = null;
        if (hit != null) {
            Anchor a = anchorOf(hit, eye, true);
            double rise = a.y - eye.y;
            double dist = Math.sqrt(sq(a.x - eye.x) + sq(a.y - eye.y) + sq(a.z - eye.z));
            if (rise >= AIMED_RISE) return a;
            if (rise < AscentRules.TETHER_SWING_MIN_RISE && dist >= ZIP_MIN) return a;
            if (dist >= ZIP_MIN) fallback = a;
        }
        Anchor best = null;
        double bestScore = 0;
        for (float up : FAN_UP) {
            for (float dy : FAN_YAW) {
                BlockHitResult h = ray(level, who, eye, yaw + dy, -up, range);
                if (h == null) continue;
                Anchor a = anchorOf(h, eye, false);
                double score = score(a, eye, yaw);
                if (score > bestScore) {
                    bestScore = score;
                    best = a;
                }
            }
        }
        return best != null ? best : fallback;
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * A good swing: well above you (best ~14), ahead of you (best ~13 out), in the way you look, and off to one side of
     * your line (3-9 out): the arc's bottom passes under the anchor, so an anchor on a trunk dead ahead swings you into
     * that trunk, while one beside your path swings you past it.
     */
    static double score(Anchor a, Vec3 eye, float yaw) {
        double dx = a.x - eye.x, dy = a.y - eye.y, dz = a.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (dy < 4 || h < 3) return 0;
        double fx = AscentRules.fx(yaw), fz = AscentRules.fz(yaw);
        double ahead = (dx * fx + dz * fz) / h;
        if (ahead < 0.45) return 0;
        double side = Math.abs(dx * -fz + dz * fx);
        double sideFit = 0.25 + 0.75 * Mth.clamp((side - 1.5) / 2.0, 0, 1) * (1 - Mth.clamp((side - 9) / 6, 0, 1));
        double riseFit = Math.exp(-sq((dy - 14) / 9));
        double reachFit = Math.exp(-sq((h - 13) / 9));
        return ahead * riseFit * reachFit * sideFit;
    }

    private static Anchor anchorOf(BlockHitResult h, Vec3 eye, boolean aimed) {
        Vec3 l = h.getLocation();
        var n = h.getDirection().getUnitVec3();
        return new Anchor(l.x + n.x * 0.04, l.y + n.y * 0.04, l.z + n.z * 0.04, aimed);
    }

    static @Nullable BlockHitResult ray(Level level, Entity who, Vec3 eye, float yaw, float pitch, double range) {
        double cp = Mth.cos(pitch * Mth.DEG_TO_RAD);
        Vec3 dir = new Vec3(AscentRules.fx(yaw) * cp, -Mth.sin(pitch * Mth.DEG_TO_RAD), AscentRules.fz(yaw) * cp);
        Vec3 end = eye.add(dir.scale(range));
        BlockHitResult h = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        return h.getType() == HitResult.Type.BLOCK ? h : null;
    }

    /**
     * Whether the rope's line from the hand to the anchor is still clear. Only the middle of the rope counts: near the
     * anchor it wraps its own trunk or branch as you swing round it, and near the hand it brushes whatever you pass;
     * something solid across the middle (another trunk swung behind) cuts it. Leaves never do.
     */
    public static boolean clear(Level level, Entity who, Vec3 hand, double ax, double ay, double az) {
        return snag(level, who, hand, ax, ay, az) == null;
    }

    /**
     * Where the rope catches on something solid across its middle: the face it meets going from the hand toward the
     * anchor, a hair off it (on the block's collision, so the server's anchor check holds). Null while it's clear.
     * The swing wraps round it there (SPEC §17.6) rather than the rope being cut.
     */
    public static @Nullable Vec3 snag(Level level, Entity who, Vec3 hand, double ax, double ay, double az) {
        Vec3 a = new Vec3(ax, ay, az);
        Vec3 d = a.subtract(hand);
        double len = d.length();
        if (len < 6) return null;
        Vec3 from = hand.add(d.scale(1.5 / len)), to = hand.add(d.scale((len - 3.0) / len));
        BlockHitResult h = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        // a line of light passes through foliage
        if (h.getType() != HitResult.Type.BLOCK || level.getBlockState(h.getBlockPos()).is(net.minecraft.tags.BlockTags.LEAVES)) return null;
        net.minecraft.core.Direction f = h.getDirection();
        return h.getLocation().add(f.getStepX() * 0.06, f.getStepY() * 0.06, f.getStepZ() * 0.06);
    }

    /**
     * The block an anchor point bit (the point sits a hair off one of its faces): the neighbouring cell, within 0.15,
     * that has collision there. Null when none does (the block is gone).
     */
    public static net.minecraft.core.@Nullable BlockPos blockOf(Level level, double ax, double ay, double az) {
        net.minecraft.core.BlockPos.MutableBlockPos c = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            double x = ax + d.getStepX() * 0.15, y = ay + d.getStepY() * 0.15, z = az + d.getStepZ() * 0.15;
            c.set(Math.floor(x), Math.floor(y), Math.floor(z));
            var shape = level.getBlockState(c).getCollisionShape(level, c);
            if (!shape.isEmpty()) return c.immutable();
        }
        return null;
    }

    /** The floor under a point, or {@code y - maxDown} when there is none within reach. */
    public static double floorUnder(Level level, Entity who, double x, double y, double z, double maxDown) {
        Vec3 from = new Vec3(x, y - 0.5, z), to = new Vec3(x, y - maxDown, z);
        BlockHitResult h = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, who));
        return h.getType() == HitResult.Type.BLOCK ? h.getLocation().y : y - maxDown;
    }
}
