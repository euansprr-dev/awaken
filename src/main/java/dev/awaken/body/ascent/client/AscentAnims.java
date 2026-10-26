package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.core.api.PlayerClips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * How a body in the Ascent looks from outside (third person and every other player). The whole figure turns with what
 * it is doing: hanging from the rope it swings with it (pivoting at the shoulder, the rope arm up toward the anchor);
 * on wings and in flight it lies along its line of flight, belly down and head first, banked into its turns; hovering
 * it stands, leaning a little into the look; let go of a rope, it tucks through one forward flip and opens.
 * The limbs come from full-body clips (Player Animation Library, tools/art/specs/ascent_moves.py) when the library is
 * there. Fixed slots, nothing allocated per frame.
 */
public final class AscentAnims {
    private AscentAnims() {}

    static final String SWING = "ascent_swing", ZIP = "ascent_zip", FLIP = "ascent_flip", FALL = "ascent_fall", GLIDE = "ascent_glide",
            SOAR = "ascent_soar", HOVER = "ascent_hover";
    /** The rappel (tools/art/specs/ascent_rappel.py): feet on the wall holding, walking down it, the kick and bound. */
    static final String RAPPEL = "ascent_rappel", RAPPEL_WALK = "ascent_rappel_walk", RAPPEL_KICK = "ascent_rappel_kick";
    /** How long the kick's clip plays (its bound), and how far the figure is drawn out from the wall it stands on. */
    static final int KICK_TICKS = 14;
    static final double RAPPEL_OUT = 0.5;
    /** The figure leans back from the wall by this share of the rope's line (about 10° hanging against it). */
    private static final double RAPPEL_LEAN_BACK = 0.18;

    private static final class Look {
        int entity = -1;
        String clip;
        int since;
        float bank, bankO;
        float heading = Float.NaN;
        /** A barrel roll: when it began (client clock) and its way (+1 right, -1 left). */
        int rollAt = -100, rollDir;
        /** A rappel's kick off the wall (client clock). */
        int kickAt = -100;
        AscentState state = AscentState.NONE;
    }

    private static final Look[] LOOKS = new Look[32];
    private static final Quaternionf Q = new Quaternionf(), Q2 = new Quaternionf();
    private static final Matrix3f A = new Matrix3f(), B = new Matrix3f();
    private static final double[] F = new double[9];
    private static int pushed;

    static {
        for (int i = 0; i < LOOKS.length; i++) LOOKS[i] = new Look();
    }

    private static Look find(int id) {
        for (Look l : LOOKS) if (l.entity == id) return l;
        return null;
    }

    private static Look slot(int id) {
        Look l = find(id);
        if (l != null) return l;
        for (Look f : LOOKS) if (f.entity < 0) {
            f.entity = id;
            f.clip = null;
            f.heading = Float.NaN;
            f.bank = f.bankO = 0;
            return f;
        }
        return LOOKS[0];
    }

    static void play(int entityId, AscentState st, AscentState was) {
        Look l = slot(entityId);
        l.state = st;
        l.since = AscentMoves.ticks;
        if (st == AscentState.CARRY && was.rope) l.clip = null; // the flip picks itself in tick()
    }

    static void stop(int entityId) {
        Look l = find(entityId);
        if (l == null) return;
        if (l.clip != null) PlayerClips.stop(entityId);
        l.entity = -1;
    }

    static void reset() {
        for (Look l : LOOKS) {
            if (l.entity >= 0 && l.clip != null) PlayerClips.stop(l.entity);
            l.entity = -1;
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // the local player is driven from the prediction
        if (mc.player != null) {
            AscentState st = AscentMoves.state();
            Look l = find(mc.player.getId());
            if (st != AscentState.NONE) {
                if (l == null || l.state != st) {
                    AscentState was = l == null ? AscentState.NONE : l.state;
                    play(mc.player.getId(), st, was);
                }
            } else if (l != null) {
                stop(mc.player.getId());
            }
        }
        for (Look l : LOOKS) {
            if (l.entity < 0) continue;
            Entity e = mc.level.getEntity(l.entity);
            if (e == null) {
                l.entity = -1;
                continue;
            }
            // bank: how fast the heading turns, felt more at speed
            double vx = e.getX() - e.xo, vz = e.getZ() - e.zo;
            double h = Math.sqrt(vx * vx + vz * vz);
            l.bankO = l.bank;
            if (h > 0.05) {
                float heading = (float) (Mth.atan2(-vx, vz) * Mth.RAD_TO_DEG);
                float turn = Float.isNaN(l.heading) ? 0 : Mth.wrapDegrees(heading - l.heading);
                l.heading = heading;
                float target = Mth.clamp(turn * 4.5F * (float) Math.min(1, h / 0.9), -55F, 55F);
                l.bank += (target - l.bank) * 0.2F;
            } else {
                l.bank *= 0.8F;
            }
            String want = clipFor(l, e);
            if (want != null && !want.equals(l.clip)) {
                if (PlayerClips.play(l.entity, want, l.clip == null ? 3 : 4)) l.clip = want;
            }
        }
    }

    private static String clipFor(Look l, Entity e) {
        int age = AscentMoves.ticks - l.since;
        return switch (l.state) {
            case TETHER -> SWING;
            case RAPPEL -> AscentMoves.ticks - l.kickAt < KICK_TICKS ? RAPPEL_KICK
                    : !rappelWall(e, NRM) ? SWING
                    : (e == Minecraft.getInstance().player ? AscentMoves.payNow > 0 : e.getY() - e.yo < -0.04) ? RAPPEL_WALK : RAPPEL;
            case ZIP -> ZIP;
            case CARRY -> age < 12 && l.clip != null && l.clip.equals(FLIP) || age < 2 ? FLIP : age < 12 ? l.clip : FALL;
            case GLIDE -> GLIDE;
            case SOAR -> speedOf(e) > 0.45 ? SOAR : HOVER;
            case NONE -> null;
        };
    }

    /** A rappel's kick for this entity (the local player's, or one heard from another): the kick's clip and bound. */
    static void kick(int entityId) {
        Look l = find(entityId);
        if (l != null) l.kickAt = AscentMoves.ticks;
    }

    private static final double[] NRM = new double[2];
    private static final net.minecraft.core.BlockPos.MutableBlockPos WALL_AT = new net.minecraft.core.BlockPos.MutableBlockPos();

    /**
     * A rappelling body's wall: its outward normal into out[0..1] (the local player's own probe, else away from the
     * anchor's line, which runs down the wall's face), and whether there's rock that way at the feet. Without it the
     * body hangs free and looks like any rope's swing.
     */
    static boolean rappelWall(Entity e, double[] out) {
        Minecraft mc = Minecraft.getInstance();
        double ax, az;
        if (e == mc.player) {
            if (AscentMoves.wallNX != 0 || AscentMoves.wallNZ != 0) {
                out[0] = AscentMoves.wallNX;
                out[1] = AscentMoves.wallNZ;
                return true;
            }
            ax = AscentMoves.ax;
            az = AscentMoves.az;
        } else {
            AscentRemote.Slot r = AscentRemote.of(e.getId());
            if (r == null) return false;
            ax = r.ax;
            az = r.az;
        }
        double nx = e.getX() - ax, nz = e.getZ() - az, nl = Math.sqrt(nx * nx + nz * nz);
        if (nl < 0.05) return false;
        out[0] = nx / nl;
        out[1] = nz / nl;
        double wx = ax - out[0] * 0.2, wz = az - out[1] * 0.2;
        return solid(e, wx, e.getY() + 0.4, wz) || solid(e, wx, e.getY() + 1.0, wz);
    }

    private static boolean solid(Entity e, double x, double y, double z) {
        WALL_AT.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return !e.level().getBlockState(WALL_AT).getCollisionShape(e.level(), WALL_AT).isEmpty();
    }

    /**
     * Where a rappelling figure is drawn off its body: out from the wall by {@link #RAPPEL_OUT} (a body on the rope
     * stands off the rock on its legs; the collision box sits against it). Into out[0..1]; false (zero) for any other.
     */
    static boolean rappelOffset(Entity e, AscentState st, double[] out) {
        if (st != AscentState.RAPPEL || !rappelWall(e, out)) {
            out[0] = 0;
            out[1] = 0;
            return false;
        }
        out[0] *= RAPPEL_OUT;
        out[1] *= RAPPEL_OUT;
        return true;
    }

    private static double speedOf(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player) return AscentMoves.speed();
        double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    // ---- the figure's frame ---------------------------------------------------------------------------------------------

    /**
     * The direction a body faces (chest), its up (head) and its right, as the renderer draws it now: into
     * out[0..2], out[3..5], out[6..8]. Upright facing its yaw when nothing turns it.
     */
    /** A barrel roll for this entity (the local player's, or one heard from another). */
    static void roll(int entityId, int dir) {
        Look l = find(entityId);
        if (l == null) return;
        l.rollAt = AscentMoves.ticks;
        l.rollDir = dir;
    }

    static void frame(Entity e, float pt, double[] out) {
        Minecraft mc = Minecraft.getInstance();
        Look l = find(e.getId());
        AscentState st = e == mc.player ? AscentMoves.state() : AscentRemote.stateOf(e);
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double vx, vy, vz;
        if (e == mc.player && st != AscentState.NONE) {
            vx = AscentMoves.K.vx;
            vy = AscentMoves.K.vy;
            vz = AscentMoves.K.vz;
        } else {
            vx = e.getX() - e.xo;
            vy = e.getY() - e.yo;
            vz = e.getZ() - e.zo;
        }
        double s = Math.sqrt(vx * vx + vy * vy + vz * vz);
        float bank = l == null ? 0 : Mth.lerp(pt, l.bankO, l.bank);
        if (l != null) {
            // a barrel roll: one whole turn about the line of flight, quick in and out
            float rt = (AscentMoves.ticks - l.rollAt + pt) / dev.awaken.body.ascent.AscentRules.ROLL_TICKS;
            if (rt >= 0 && rt < 1) bank += -l.rollDir * 360F * (rt * rt * (3 - 2 * rt));
        }
        // defaults: upright
        double ux = 0, uy = 1, uz = 0, cx = fx, cy = 0, cz = fz;
        switch (st) {
            case TETHER, ZIP, RAPPEL -> {
                double ax, ay, az;
                if (e == mc.player) {
                    ax = AscentMoves.ax;
                    ay = AscentMoves.ay;
                    az = AscentMoves.az;
                } else {
                    AscentRemote.Slot r = AscentRemote.of(e.getId());
                    if (r == null) break;
                    ax = r.ax;
                    ay = r.ay;
                    az = r.az;
                }
                double px = Mth.lerp(pt, e.xo, e.getX()), py = Mth.lerp(pt, e.yo, e.getY()) + 1.4, pz = Mth.lerp(pt, e.zo, e.getZ());
                double dx = ax - px, dy = ay - py, dz = az - pz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-3) break;
                if (st == AscentState.RAPPEL && rappelWall(e, NRM)) {
                    // the rappel: along the rope, leaning back off the wall, facing it (the feet on the rock)
                    ux = dx / d + NRM[0] * RAPPEL_LEAN_BACK;
                    uy = dy / d;
                    uz = dz / d + NRM[1] * RAPPEL_LEAN_BACK;
                    cx = -NRM[0];
                    cy = 0;
                    cz = -NRM[1];
                    break;
                }
                // a zip leans into the pull; a swing hangs straight from the rope
                double k = st == AscentState.ZIP ? 0.65 : 0.9;
                ux = dx / d * k;
                uy = dy / d * k + (1 - k);
                uz = dz / d * k;
                // facing: along the swing (or the yaw), kept square to the rope
                if (s > 0.15) {
                    cx = vx / s;
                    cy = vy / s;
                    cz = vz / s;
                }
            }
            case GLIDE, SOAR -> {
                boolean hover = st == AscentState.SOAR && s < 0.45;
                if (hover) {
                    // standing in the air, leaning a little into the look
                    ux = fx * 0.2;
                    uy = 1;
                    uz = fz * 0.2;
                } else if (s > 0.05) {
                    // lying along the line of flight: head first, belly down
                    ux = vx / s;
                    uy = vy / s;
                    uz = vz / s;
                    cx = 0;
                    cy = -1;
                    cz = 0;
                    // a near-vertical dive keeps facing its heading rather than spinning
                    if (Math.abs(uy) > 0.97) {
                        cx = fx * Math.signum(-uy) * -1;
                        cy = 0;
                        cz = fz * Math.signum(-uy) * -1;
                    }
                }
            }
            case CARRY -> {
                if (s > 0.3) {
                    // a slight lean into the flight
                    ux = vx / s * 0.3;
                    uy = 1;
                    uz = vz / s * 0.3;
                }
            }
            case NONE -> {
            }
        }
        // orthonormalise: up, then facing square to it, then right = facing x up
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double dot = cx * ux + cy * uy + cz * uz;
        cx -= ux * dot;
        cy -= uy * dot;
        cz -= uz * dot;
        double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-4) {
            // facing collapsed onto up: use the yaw's way
            cx = fx;
            cy = 0;
            cz = fz;
            dot = cx * ux + cy * uy + cz * uz;
            cx -= ux * dot;
            cy -= uy * dot;
            cz -= uz * dot;
            cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        }
        cx /= cl;
        cy /= cl;
        cz /= cl;
        double rx = cy * uz - cz * uy, ry = cz * ux - cx * uz, rz = cx * uy - cy * ux;
        // bank: roll about the up axis for flight (head-first), about the facing axis for a standing body
        if (Math.abs(bank) > 0.1 && (st == AscentState.GLIDE || st == AscentState.SOAR)) {
            double b = bank * Mth.DEG_TO_RAD, c = Math.cos(b), sn = Math.sin(b);
            double ncx = cx * c + rx * sn, ncy = cy * c + ry * sn, ncz = cz * c + rz * sn;
            double nrx = rx * c - cx * sn, nry = ry * c - cy * sn, nrz = rz * c - cz * sn;
            cx = ncx;
            cy = ncy;
            cz = ncz;
            rx = nrx;
            ry = nry;
            rz = nrz;
        }
        out[0] = cx;
        out[1] = cy;
        out[2] = cz;
        out[3] = ux;
        out[4] = uy;
        out[5] = uz;
        out[6] = rx;
        out[7] = ry;
        out[8] = rz;
    }

    // ---- the whole figure -----------------------------------------------------------------------------------------------

    static void onPre(RenderPlayerEvent.Pre<?> e) {
        AvatarRenderState s = e.getRenderState();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity ent = mc.level.getEntity(s.id);
        if (ent == null) return;
        AscentState st = ent == mc.player ? AscentMoves.state() : AscentRemote.stateOf(ent);
        if (st == AscentState.NONE) return;
        float pt = s.partialTick;
        frame(ent, pt, F);
        // the renderer will turn the model by its body yaw; turn the upright, yaw-facing frame onto ours first
        float yaw = s.bodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // columns: right, up, facing (right = facing x up = (-fz, 0, fx) for an upright body)
        A.set((float) -fz, 0, (float) fx, 0, 1, 0, (float) fx, 0, (float) fz);
        B.set((float) F[6], (float) F[7], (float) F[8], (float) F[3], (float) F[4], (float) F[5], (float) F[0], (float) F[1], (float) F[2]);
        B.mul(A.transpose());
        Q.setFromNormalized(B);
        float pivot = st.rope ? 1.4F : 0.9F;
        // a flip after letting go: one forward turn about the hips over the first 12 ticks
        float flip = 0;
        if (st == AscentState.CARRY) {
            Look l = find(s.id);
            float age = l == null ? 99 : AscentMoves.ticks - l.since + pt;
            if (l != null && FLIP.equals(l.clip) && age < 12) {
                float u = age / 12F;
                flip = 360F * (u * u * (3 - 2 * u));
            }
        }
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        pushed++;
        // a rappel stands off the rock on its legs
        if (rappelOffset(ent, st, NRM)) ps.translate((float) NRM[0], 0, (float) NRM[1]);
        ps.translate(0, pivot, 0);
        ps.rotate(Q);
        if (flip != 0) ps.rotate(Q2.rotationAxis(flip * Mth.DEG_TO_RAD, Mth.cos(yaw), 0, Mth.sin(yaw)));
        ps.translate(0, -pivot, 0);
    }

    static void onPost(RenderPlayerEvent.Post<?> e) {
        if (pushed > 0) {
            e.getPoseStack().popPose();
            pushed--;
        }
    }
}
