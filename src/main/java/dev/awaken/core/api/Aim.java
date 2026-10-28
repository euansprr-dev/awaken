package dev.awaken.core.api;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * The creature you mean (SPEC §40, lock-on). The client's aim module marks the creature nearest the middle of the view
 * and can lock onto it; anyone may ask which with {@link #target()} / {@link #locked()} (client). The server never takes
 * the client's word alone: a press names its creature and {@link #meant} checks it (alive, in reach, near the press's
 * look, seen), and only a creature it passes is struck as the meant one. The numbers both sides share live here.
 */
public final class Aim {
    private Aim() {}

    /** How far from the press's look the meant creature may be (degrees, to the edge of its box). */
    public static final float AIM_CAP = 35F;
    /** A person is struck as meant only right under the view: lock-on never picks people. */
    public static final float PERSON_CAP = 8F;
    /** Room for a moving creature between the press and its check (blocks). */
    public static final float PRESS_SLACK = 0.6F;
    /** A flier brought down by a stance break or a deflected dive stays on the ground this long (2.5 s). */
    public static final int GROUNDED_TICKS = 50;

    /**
     * The step a chain's first strike or a heavy takes toward the meant creature (blocks): light weapons step further,
     * the long and the heavy ones barely.
     */
    public static float stepIn(String style) {
        return switch (style) {
            case "daggers", "gauntlets", "twinblades" -> 1.2F;
            case "maul", "greatblade" -> 0.6F;
            case "spear", "glaive", "chainblade" -> 0.4F;
            default -> 1.0F;
        };
    }

    // ---- the server's check -----------------------------------------------------------------------------------------

    /**
     * Whether {@code t} can be the creature a press at ({@code yaw}, {@code pitch}) meant: alive, in this level, within
     * {@code reach} (+ slack) of the eye, within {@link #AIM_CAP} of the press's look or the body's (a person: within
     * {@link #PERSON_CAP}), and seen from the eye.
     */
    public static boolean meant(LivingEntity p, @Nullable Entity t, float yaw, float pitch, double reach) {
        if (t == null || t == p || !t.isAlive() || t.level() != p.level()) return false;
        Vec3 eye = p.getEyePosition();
        AABB b = t.getBoundingBox();
        if (gap(eye, b) > reach + PRESS_SLACK) return false;
        float cap = t instanceof Player ? PERSON_CAP : AIM_CAP;
        float off = Math.min(offEdge(eye, b, yaw, pitch), offEdge(eye, b, p.getYRot(), p.getXRot()));
        return off <= cap && sees(p, t);
    }

    /** The id {@link #meant} lets through, or -1. */
    public static int meantId(LivingEntity p, int id, float yaw, float pitch, double reach) {
        if (id < 0) return -1;
        return meant(p, p.level().getEntity(id), yaw, pitch, reach) ? id : -1;
    }

    /** The eye's distance to the nearest point of a box. */
    public static double gap(Vec3 eye, AABB b) {
        double x = Mth.clamp(eye.x, b.minX, b.maxX) - eye.x, y = Mth.clamp(eye.y, b.minY, b.maxY) - eye.y, z = Mth.clamp(eye.z, b.minZ, b.maxZ) - eye.z;
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Degrees between the look at ({@code yaw}, {@code pitch}) and the box, measured to its edge (0 when the look is on it). */
    public static float offEdge(Vec3 eye, AABB b, float yaw, float pitch) {
        Vec3 c = b.getCenter();
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-4) return 0F;
        float ang = angle(yaw, pitch, dx, dy, dz);
        double r = 0.5 * Math.sqrt(b.getXsize() * b.getXsize() + b.getYsize() * b.getYsize() + b.getZsize() * b.getZsize());
        float edge = (float) (Math.atan2(Math.min(r, d), d) * Mth.RAD_TO_DEG);
        return Math.max(0F, ang - edge);
    }

    /** Degrees between the look at ({@code yaw}, {@code pitch}) (Minecraft's: yaw 0 = +z) and a direction. */
    public static float angle(float yaw, float pitch, double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return 0F;
        float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
        double cp = Math.cos(pr);
        double lx = -Math.sin(yr) * cp, ly = -Math.sin(pr), lz = Math.cos(yr) * cp;
        double dot = (lx * dx + ly * dy + lz * dz) / len;
        return (float) (Math.acos(Mth.clamp(dot, -1.0, 1.0)) * Mth.RAD_TO_DEG);
    }

    /** The eye sees the creature's eye or its middle (a body half round a corner still counts). */
    public static boolean sees(Entity from, Entity t) {
        Vec3 eye = from.getEyePosition();
        return clear(from, eye, t.getEyePosition()) || clear(from, eye, t.getBoundingBox().getCenter());
    }

    private static boolean clear(Entity from, Vec3 a, Vec3 b) {
        return from.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from)).getType() == HitResult.Type.MISS;
    }

    // ---- the client's pick (installed by the aim module) ------------------------------------------------------------

    private static IntSupplier target = () -> -1;
    private static java.util.function.BooleanSupplier locked = () -> false;

    public static void install(IntSupplier targetId, java.util.function.BooleanSupplier isLocked) {
        target = targetId;
        locked = isLocked;
    }

    /** The id of the creature locked or marked on this client, or -1 (client only; -1 on a server). */
    public static int target() {
        return target.getAsInt();
    }

    /** Whether the player has locked on (not just a mark). */
    public static boolean locked() {
        return locked.getAsBoolean();
    }
}
