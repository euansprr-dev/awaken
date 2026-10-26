package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.AscentState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Ascent's light, as data the renderer draws: one-off shapes (a rope's bite, a sonic boom's shock ring, a rope
 * that missed or was let go), contrails behind anyone moving fast (off the wingtips while the wings are open, off the
 * hands otherwise), and the motes of air that stream past your own eyes at speed. Fixed pools, nothing allocated per
 * tick; the motes it throws are its own particle ({@code awaken:ascent_mote}).
 */
final class AscentFx {
    private AscentFx() {}

    static final int BITE = 0, RING = 1, LOOSE = 2, MISS = 3;

    static final class Shot {
        int kind = -1;
        int entity;
        double x, y, z, nx, ny, nz;
        int age, life;
        /** A rope's line (RopeKit.LINE_*), for its look as it falls away. */
        int line;
        float size;
    }

    static final Shot[] SHOTS = new Shot[40];

    /** A contrail: two ribbons of points behind a body, newest first. */
    static final class Trail {
        static final int N = 22;
        int entity = -1;
        final double[] lx = new double[N], ly = new double[N], lz = new double[N];
        final double[] rx = new double[N], ry = new double[N], rz = new double[N];
        int count;
        float alpha, width;
        boolean wings;
        int lastTick;
    }

    static final Trail[] TRAILS = new Trail[12];

    /** Air streaming past the local player's eyes. */
    static final int MOTES = 56;
    static final double[] MX = new double[MOTES], MY = new double[MOTES], MZ = new double[MOTES];
    static final int[] MAGE = new int[MOTES];
    static double moteVx, moteVy, moteVz, moteSpeed;

    static {
        for (int i = 0; i < SHOTS.length; i++) SHOTS[i] = new Shot();
        for (int i = 0; i < TRAILS.length; i++) TRAILS[i] = new Trail();
        java.util.Arrays.fill(MAGE, -1);
    }

    private static Shot shot(int kind, int life) {
        Shot best = SHOTS[0];
        for (Shot s : SHOTS) {
            if (s.kind < 0) {
                best = s;
                break;
            }
            if (s.age > best.age) best = s;
        }
        best.kind = kind;
        best.age = 0;
        best.life = life;
        return best;
    }

    // ---- moments --------------------------------------------------------------------------------------------------

    /** Where a rope bites: a flash and a spray of sparks off the face. */
    static void bite(Entity who, double x, double y, double z) {
        Shot s = shot(BITE, 7);
        s.entity = who.getId();
        s.x = x;
        s.y = y;
        s.z = z;
        s.size = 0.9F;
        if (!(who.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        Vec3 back = who.position().add(0, 1.2, 0).subtract(x, y, z).normalize();
        for (int i = 0; i < 10; i++) {
            MoteParticle.next(0xDDF4FF, 0.55F, 8 + r.nextInt(6), 0.04F, true);
            level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), x, y, z,
                    back.x * 0.12 + (r.nextDouble() - 0.5) * 0.22, back.y * 0.12 + (r.nextDouble() - 0.4) * 0.22,
                    back.z * 0.12 + (r.nextDouble() - 0.5) * 0.22);
        }
    }

    private static final net.minecraft.core.BlockPos.MutableBlockPos GRIT_AT = new net.minecraft.core.BlockPos.MutableBlockPos();

    /**
     * Grit knocked off a wall by the feet (the rappel's kick and its landing back on the rock): the rock's own colour,
     * dulled toward dust, lit by the world (it's stone, not light), thrown out along the wall's normal (nx, nz) and
     * falling.
     */
    static void grit(Entity who, double x, double y, double z, double nx, double nz, int count) {
        if (!(who.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        GRIT_AT.set(net.minecraft.util.Mth.floor(x - nx * 0.3), net.minecraft.util.Mth.floor(y), net.minecraft.util.Mth.floor(z - nz * 0.3));
        var s = level.getBlockState(GRIT_AT);
        int col = s.isAir() ? 0x8A8378 : s.getMapColor(level, GRIT_AT).col;
        if (col == 0) col = 0x8A8378;
        int tint = (((col >> 16) & 255) * 7 / 10 + 0x8E * 3 / 10) << 16 | (((col >> 8) & 255) * 7 / 10 + 0x88 * 3 / 10) << 8
                | ((col & 255) * 7 / 10 + 0x7C * 3 / 10);
        for (int i = 0; i < count; i++) {
            MoteParticle.nextGrit(tint, 0.9F + r.nextFloat() * 0.6F, 14 + r.nextInt(8), 0.7F);
            level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), x + (r.nextDouble() - 0.5) * 0.4, y + r.nextDouble() * 0.3,
                    z + (r.nextDouble() - 0.5) * 0.4, nx * (0.05 + r.nextDouble() * 0.08) + (r.nextDouble() - 0.5) * 0.06,
                    0.02 + r.nextDouble() * 0.05, nz * (0.05 + r.nextDouble() * 0.08) + (r.nextDouble() - 0.5) * 0.06);
        }
    }

    /** A Deepglass line paying out: a violet glint shed where the rope runs through the hand, drifting off it. */
    static void glint(Entity who, double x, double y, double z) {
        if (!(who.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        MoteParticle.next(r.nextInt(3) == 0 ? 0xF4ECFF : 0xB48CFF, 0.45F + r.nextFloat() * 0.3F, 9 + r.nextInt(6), -0.01F, false);
        level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), x, y, z, (r.nextDouble() - 0.5) * 0.03, 0.02 + r.nextDouble() * 0.02,
                (r.nextDouble() - 0.5) * 0.03);
    }

    /** A rope let go: it stays on its anchor and falls away from where the hand was, fading (the follow-through). */
    static void loose(int entityId, double ax, double ay, double az) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e == null) return;
        Shot s = shot(LOOSE, 9);
        s.entity = entityId;
        AscentRemote.Slot slot = e == mc.player ? null : AscentRemote.of(entityId);
        s.line = e == mc.player ? AscentMoves.kitLine : slot == null ? 0 : slot.kit & 3;
        s.x = ax;
        s.y = ay;
        s.z = az;
        s.nx = e.getX();
        s.ny = e.getY() + AscentRules.HAND_HEIGHT;
        s.nz = e.getZ();
    }

    /** A rope that found nothing: out to where it flew, then falling back. */
    static void miss(Entity who, double x, double y, double z) {
        Shot s = shot(MISS, 10);
        s.entity = who.getId();
        s.x = x;
        s.y = y;
        s.z = z;
    }

    /** The sonic boom: a ring of shocked air across the line of flight, and a cone of vapour shed behind. */
    static void boom(LocalPlayer p, AscentRules.Kin k) {
        ring(p.getX(), p.getY() + 0.9, p.getZ(), new Vec3(k.vx, k.vy, k.vz), 1F);
        ClientLevel level = (ClientLevel) p.level();
        RandomSource r = level.getRandom();
        double s = k.speed();
        double dx = k.vx / s, dy = k.vy / s, dz = k.vz / s;
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            // a direction across the flight
            double ux = -dz, uz = dx, uy = 0;
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1e-3) {
                ux = 1;
                uz = 0;
                ul = 1;
            }
            ux /= ul;
            uz /= ul;
            double wx = dy * uz - dz * uy, wy = dz * ux - dx * uz, wz = dx * uy - dy * ux;
            double ox = Math.cos(a) * ux + Math.sin(a) * wx, oy = Math.cos(a) * uy + Math.sin(a) * wy, oz = Math.cos(a) * uz + Math.sin(a) * wz;
            MoteParticle.next(0xF2F7FF, 1.3F, 14 + r.nextInt(10), 0F, false);
            level.addParticle(dev.awaken.body.ascent.AscentContent.MOTE.get(), p.getX() + ox * 0.6, p.getY() + 0.9 + oy * 0.6, p.getZ() + oz * 0.6,
                    ox * 0.35 - dx * 0.4, oy * 0.35 - dy * 0.4, oz * 0.35 - dz * 0.4);
        }
    }

    static void ring(double x, double y, double z, Vec3 dir, float size) {
        Shot s = shot(RING, 22);
        s.x = x;
        s.y = y;
        s.z = z;
        double l = dir.length();
        if (l < 1e-4) {
            s.nx = 0;
            s.ny = 1;
            s.nz = 0;
        } else {
            s.nx = dir.x / l;
            s.ny = dir.y / l;
            s.nz = dir.z / l;
        }
        s.size = size;
    }

    // ---- ticking --------------------------------------------------------------------------------------------------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        for (Shot s : SHOTS) if (s.kind >= 0 && ++s.age > s.life) s.kind = -1;
        if (mc.level == null) return;
        // contrails: the local player and everyone the server shows in the air
        if (mc.player != null) sampleTrail(mc.player, AscentMoves.state(), AscentMoves.speed());
        for (AscentRemote.Slot r : AscentRemote.SLOTS) {
            if (r.entity < 0) continue;
            Entity e = mc.level.getEntity(r.entity);
            if (e == null) continue;
            double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
            sampleTrail(e, r.state, Math.sqrt(vx * vx + vy * vy + vz * vz));
        }
        for (Trail t : TRAILS) {
            if (t.entity >= 0 && AscentMoves.ticks - t.lastTick > 1) {
                // nobody is feeding it any more: let it shrink away
                if (t.count > 0) t.count--;
                if (t.count == 0) t.entity = -1;
            }
        }
        if (mc.player != null) motes(mc.player);
    }

    private static void sampleTrail(Entity e, AscentState st, double speed) {
        boolean on = st.airborne() && speed > (st.wings ? 0.55 : 1.15);
        Trail t = trail(e.getId(), on);
        if (t == null) return;
        if (!on) return;
        t.lastTick = AscentMoves.ticks;
        t.wings = st.wings;
        float target = (float) Mth.clamp((speed - (st.wings ? 0.55 : 1.15)) / 1.2, 0, 1);
        t.alpha += (target - t.alpha) * 0.3F;
        t.width = st.wings ? 0.055F : 0.03F;
        System.arraycopy(t.lx, 0, t.lx, 1, Trail.N - 1);
        System.arraycopy(t.ly, 0, t.ly, 1, Trail.N - 1);
        System.arraycopy(t.lz, 0, t.lz, 1, Trail.N - 1);
        System.arraycopy(t.rx, 0, t.rx, 1, Trail.N - 1);
        System.arraycopy(t.ry, 0, t.ry, 1, Trail.N - 1);
        System.arraycopy(t.rz, 0, t.rz, 1, Trail.N - 1);
        emitters(e, st, 1F, EMIT);
        t.lx[0] = EMIT[0];
        t.ly[0] = EMIT[1];
        t.lz[0] = EMIT[2];
        t.rx[0] = EMIT[3];
        t.ry[0] = EMIT[4];
        t.rz[0] = EMIT[5];
        if (t.count < Trail.N) t.count++;
    }

    static final double[] EMIT = new double[6];

    /** Where a body's contrails leave it: the wingtips while the wings are open, else the hands. Writes (lx..rz). */
    static void emitters(Entity e, AscentState st, float pt, double[] out) {
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()) + 0.9, z = Mth.lerp(pt, e.zo, e.getZ());
        AscentAnims.frame(e, pt, FRAME);
        // FRAME: forward (0..2), up (3..5), right (6..8)
        double span, up, back;
        if (st.wings) {
            span = 2.05;
            up = 0.45;
            back = -0.6;
        } else {
            span = 0.42;
            up = 0.1;
            back = 0;
        }
        double cx = x + FRAME[3] * up + FRAME[0] * back, cy = y + FRAME[4] * up + FRAME[1] * back, cz = z + FRAME[5] * up + FRAME[2] * back;
        out[0] = cx - FRAME[6] * span;
        out[1] = cy - FRAME[7] * span;
        out[2] = cz - FRAME[8] * span;
        out[3] = cx + FRAME[6] * span;
        out[4] = cy + FRAME[7] * span;
        out[5] = cz + FRAME[8] * span;
    }

    private static final double[] FRAME = new double[9];

    private static Trail trail(int entity, boolean make) {
        for (Trail t : TRAILS) if (t.entity == entity) return t;
        if (!make) return null;
        Trail best = null;
        for (Trail t : TRAILS) if (t.entity < 0) {
            best = t;
            break;
        }
        if (best == null) return null;
        best.entity = entity;
        best.count = 0;
        best.alpha = 0;
        return best;
    }

    /** Motes of air ahead of you that stream past: born in a sleeve around your line of flight, gone once behind. */
    private static void motes(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        double s = AscentMoves.speed();
        moteSpeed = s;
        AscentRules.Kin k = AscentMoves.K;
        if (s > 1e-3) {
            moteVx = k.vx / s;
            moteVy = k.vy / s;
            moteVz = k.vz / s;
        }
        double ex = p.getX(), ey = p.getEyeY(), ez = p.getZ();
        for (int i = 0; i < MOTES; i++) {
            if (MAGE[i] < 0) continue;
            MAGE[i]++;
            double dx = MX[i] - ex, dy = MY[i] - ey, dz = MZ[i] - ez;
            if (MAGE[i] > 40 || dx * moteVx + dy * moteVy + dz * moteVz < -2 || !st.airborne()) MAGE[i] = -1;
        }
        if (!st.airborne() || s < 0.7) return;
        RandomSource r = p.getRandom();
        int spawn = (int) Math.min(6, (s - 0.7) * 4) + (r.nextFloat() < ((s - 0.7) * 4) % 1 ? 1 : 0);
        for (int n = 0; n < spawn; n++) {
            int slot = -1;
            for (int i = 0; i < MOTES; i++) if (MAGE[i] < 0) {
                slot = i;
                break;
            }
            if (slot < 0) return;
            // across the flight: two unit vectors
            double ux = -moteVz, uz = moteVx, ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1e-3) {
                ux = 1;
                uz = 0;
                ul = 1;
            }
            ux /= ul;
            uz /= ul;
            double wx = moteVy * uz, wy = moteVz * ux - moteVx * uz, wz = -moteVy * ux;
            double a = r.nextDouble() * Mth.TWO_PI, rad = 1.6 + r.nextDouble() * 4.5, ahead = 7 + r.nextDouble() * 12;
            MX[slot] = ex + moteVx * ahead + (Math.cos(a) * ux + Math.sin(a) * wx) * rad;
            MY[slot] = ey + moteVy * ahead + Math.sin(a) * wy * rad;
            MZ[slot] = ez + moteVz * ahead + (Math.cos(a) * uz + Math.sin(a) * wz) * rad;
            MAGE[slot] = 0;
        }
    }
}
