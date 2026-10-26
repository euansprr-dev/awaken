package dev.awaken.wildlife.client;

import dev.awaken.citizens.client.CityLife;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.wildlife.Census;
import dev.awaken.wildlife.Density;
import dev.awaken.wildlife.WildKinds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;

/**
 * The wild's insects (SPEC "Wild life"), simulated together in flat arrays (nothing allocated after start): butterflies
 * fluttering between the flowers of a sunny meadow and settling on them, wings opening and closing; bees zipping flower
 * to flower and hanging at each; dragonflies darting and hovering over still water; fireflies rising off the forest floor
 * and the marsh at dusk, each blinking to its own slow rhythm; moths round a lamp at night; a gnat swarm bobbing in the
 * evening air by the water; water striders skating on calm water, leaving rings. And the far flocks: a dozen small birds
 * crossing the sky, starlings over the open land at dusk. Each made with its own anchor from the chunk's draw, faded in
 * and out (never popped), drawn by {@link InsectRender} from our own sprite sheet. None in rain or the cold.
 */
final class Insects {
    private Insects() {}

    static final int CAP = WildKinds.MAX_INSECTS;
    static final int FLY = 0, REST = 1, DART = 2;

    // the pool: kind < 0 is a free slot
    static final int[] kind = new int[CAP], state = new int[CAP], timer = new int[CAP], variant = new int[CAP], light = new int[CAP];
    static final long[] home = new long[CAP];
    static final int[] slot = new int[CAP];
    static final double[] x = new double[CAP], y = new double[CAP], z = new double[CAP], xo = new double[CAP], yo = new double[CAP],
            zo = new double[CAP], vx = new double[CAP], vy = new double[CAP], vz = new double[CAP], ax = new double[CAP],
            ay = new double[CAP], az = new double[CAP], tx = new double[CAP], ty = new double[CAP], tz = new double[CAP];
    static final float[] alpha = new float[CAP], alphaO = new float[CAP], yaw = new float[CAP], yawO = new float[CAP],
            phase = new float[CAP], rate = new float[CAP];
    static final boolean[] leaving = new boolean[CAP];
    private static int live;

    // ripples on water: x, y, z, born (game time), size
    static final int RIPPLES = 32;
    static final double[] rx = new double[RIPPLES], ry = new double[RIPPLES], rz = new double[RIPPLES];
    static final long[] rborn = new long[RIPPLES];
    static final float[] rsize = new float[RIPPLES];
    private static int rnext;

    // the far flock (one at a time): where it set off, its heading and speed, how many, its kind (0 finches, 1 starlings)
    static boolean flock;
    static double fx, fy, fz, fdx, fdz, fspeed;
    static long fborn;
    static int fcount, fkind;
    static long nextFlock;

    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    static {
        java.util.Arrays.fill(kind, -1);
    }

    static int count() {
        return live;
    }

    static void clear() {
        java.util.Arrays.fill(kind, -1);
        live = 0;
        flock = false;
        java.util.Arrays.fill(rborn, Long.MIN_VALUE / 2);
    }

    static void counts(int[] near, int[] seen) {
        for (int i = 0; i < CAP; i++) {
            if (kind[i] < 0) continue;
            near[kind[i]]++;
            double d = Math.sqrt((x[i] - Wildlife.camX) * (x[i] - Wildlife.camX) + (y[i] - Wildlife.camY) * (y[i] - Wildlife.camY)
                    + (z[i] - Wildlife.camZ) * (z[i] - Wildlife.camZ));
            if (d < 20 && Wildlife.inView(x[i], y[i], z[i], 0.6F)) seen[kind[i]]++;
        }
    }

    static void ripple(double px, double py, double pz, float size) {
        rx[rnext] = px;
        ry[rnext] = py;
        rz[rnext] = pz;
        rborn[rnext] = Wildlife.now;
        rsize[rnext] = size;
        rnext = (rnext + 1) % RIPPLES;
    }

    // ---- who lives here (the director's half-second pass) ----------------------------------------------------------

    static void populate(ClientLevel level, Sites.Site[] order, long[] keys, int n, Census census) {
        for (int i = 0; i < CAP; i++) if (kind[i] >= 0 && !leaving[i]) census.add(kind[i]);
        for (int o = 0; o < n; o++) {
            Sites.Site s = order[(int) (keys[o] & 0xFFFFF)];
            double d = (keys[o] >>> 20) / 16.0;
            if (d > 44) break;
            boolean city = CityLife.inside(s.centreX(), s.centreZ(), 16);
            for (int k = WildKinds.BODIES; k < WildKinds.COUNT; k++) {
                if (d > WildKinds.REACH[k] && !city) continue;
                int have = 0, far = -1;
                double farD = -1;
                for (int i = 0; i < CAP; i++) {
                    if (kind[i] != k || home[i] != s.key || leaving[i] || slot[i] < 0) continue;
                    have++;
                    double dd = (x[i] - Wildlife.px) * (x[i] - Wildlife.px) + (z[i] - Wildlife.pz) * (z[i] - Wildlife.pz);
                    if (dd > farD) {
                        farD = dd;
                        far = i;
                    }
                }
                // a couple a pass at most, so a meadow fills in over seconds, not all at once
                for (int step = 0; step < 2; step++) {
                    int dec = census.decide(k, d, city, s.h, s.key, have, Wildlife.tod, Wildlife.rain, Wildlife.thunder, Wildlife.dial);
                    if (dec < 0 && far >= 0) {
                        leaving[far] = true;
                        census.remove(k);
                        break;
                    }
                    if (dec <= 0 || !spawn(level, k, s, have)) break;
                    have++;
                    census.add(k);
                }
            }
        }
        // the far flock
        if (!flock && Wildlife.now >= nextFlock) {
            nextFlock = Wildlife.now + 600 + R.nextInt(1200);
            Sites.Site here = Sites.get(net.minecraft.world.level.ChunkPos.pack(Mth.floor(Wildlife.px) >> 4, Mth.floor(Wildlife.pz) >> 4));
            int t = Wildlife.tod;
            boolean day = t < 11500 || t > 23200, dusk = t >= 11500 && t < 13000;
            if (here != null && Wildlife.rain < 0.2F && (day || dusk) && here.h.family >= 0
                    && Density.lambda(WildKinds.SONGBIRD, openProbe(here.h.family), 3000, 0F, 0F) > 0F && R.nextFloat() < 0.75F) {
                startFlock(here, dusk);
            }
        }
    }

    private static final Density.Habitat PROBE = new Density.Habitat();

    private static Density.Habitat openProbe(int family) {
        return PROBE.set(family, 0.3F, 0.8F, 6, 2, 8, 4, 0, 0, 0);
    }

    private static void startFlock(Sites.Site here, boolean dusk) {
        flock = true;
        fkind = dusk && R.nextBoolean() ? 1 : 0;
        fcount = fkind == 1 ? 18 + R.nextInt(14) : 7 + R.nextInt(8);
        // from out of sight on one side, across the sky past the player, out the other
        double a = R.nextDouble() * Math.PI * 2;
        double side = (R.nextDouble() - 0.5) * 60;
        fdx = Math.cos(a);
        fdz = Math.sin(a);
        fx = Wildlife.px - fdx * 110 - fdz * side;
        fz = Wildlife.pz - fdz * 110 + fdx * side;
        fy = here.groundY + 22 + R.nextDouble() * 22;
        fspeed = fkind == 1 ? 0.55 : 0.6;
        fborn = Wildlife.now;
    }

    private static boolean spawn(ClientLevel level, int k, Sites.Site s, int sl) {
        int i = -1;
        for (int j = 0; j < CAP; j++) {
            if (kind[j] < 0) {
                i = j;
                break;
            }
        }
        if (i < 0) return false;
        float draw = Density.draw(s.key, k, sl + 1);
        double px, py, pz;
        switch (k) {
            case WildKinds.BUTTERFLY, WildKinds.BEE -> {
                long p;
                if (s.nFlower > 0) p = s.flower[(int) (draw * s.nFlower)];
                else if (s.nGround > 0) p = s.ground[(int) (draw * s.nGround)];
                else return false;
                px = BlockPos.getX(p) + 0.5;
                py = BlockPos.getY(p) + 0.6;
                pz = BlockPos.getZ(p) + 0.5;
            }
            case WildKinds.DRAGONFLY, WildKinds.STRIDER, WildKinds.GNATS -> {
                if (s.nWater == 0) return false;
                long p = s.water[(int) (draw * s.nWater)];
                if (!Birds.still(level, BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) return false;
                px = BlockPos.getX(p) + 0.5;
                py = BlockPos.getY(p) + (k == WildKinds.STRIDER ? 0.9 : k == WildKinds.GNATS ? 2.0 : 1.3);
                pz = BlockPos.getZ(p) + 0.5;
                if (k == WildKinds.GNATS && s.nShore > 0) {
                    long q = s.shore[(int) (draw * s.nShore)];
                    px = BlockPos.getX(q) + 0.5;
                    py = BlockPos.getY(q) + 1.6;
                    pz = BlockPos.getZ(q) + 0.5;
                }
            }
            case WildKinds.FIREFLY -> {
                // over the forest floor and the meadow's edge: the ground under the canopy, else open ground
                double fx0 = s.cx * 16 + Density.draw(s.key, k, sl + 41) * 16, fz0 = s.cz * 16 + Density.draw(s.key, k, sl + 59) * 16;
                double g = Wildlife.ground(level, fx0, fz0);
                if (Double.isNaN(g) || !level.getFluidState(M.set(Mth.floor(fx0), (int) g - 1, Mth.floor(fz0))).isEmpty()) return false;
                px = fx0;
                py = g + 0.4 + Density.draw(s.key, k, sl + 77) * 2.2;
                pz = fz0;
            }
            case WildKinds.MOTH -> {
                if (s.nLight == 0) return false;
                long p = s.light[(int) (draw * s.nLight)];
                px = BlockPos.getX(p) + 0.5;
                py = BlockPos.getY(p) + 0.5;
                pz = BlockPos.getZ(p) + 0.5;
            }
            default -> {
                return false;
            }
        }
        if (Soundscape.hushAt(px, py, pz) < 0.5F) return false;
        kind[i] = k;
        home[i] = s.key;
        slot[i] = sl;
        state[i] = FLY;
        timer[i] = 20 + R.nextInt(60);
        variant[i] = (int) (Density.draw(s.key, k, sl + 97) * 4);
        if (k == WildKinds.FIREFLY && s.h.variant == 2) variant[i] = 4; // Lumen Forest's fireflies burn blue
        ax[i] = px;
        ay[i] = py;
        az[i] = pz;
        // start a little off the anchor, faded out
        double a = R.nextDouble() * Math.PI * 2;
        x[i] = xo[i] = px + Math.cos(a) * 1.2;
        y[i] = yo[i] = py + (k == WildKinds.STRIDER ? 0 : 0.3);
        z[i] = zo[i] = pz + Math.sin(a) * 1.2;
        if (k == WildKinds.STRIDER) {
            x[i] = xo[i] = px;
            z[i] = zo[i] = pz;
        }
        vx[i] = vy[i] = vz[i] = 0;
        tx[i] = px;
        ty[i] = py;
        tz[i] = pz;
        alpha[i] = alphaO[i] = 0F;
        yaw[i] = yawO[i] = R.nextFloat() * 360F;
        phase[i] = R.nextFloat() * Mth.TWO_PI;
        rate[i] = 0.85F + R.nextFloat() * 0.3F;
        leaving[i] = false;
        light[i] = LightCoordsUtil.FULL_BRIGHT;
        live++;
        return true;
    }

    /**
     * The studio (tours): a few of every insect in a band 3-6 blocks before the camera, each anchored where it's put
     * (water-bound ones on the ground there), living on the player's chunk. Cleared with the bodies' line-up.
     */
    static int demo(ClientLevel level, double px, double pz, float facing) {
        long chunk = net.minecraft.world.level.ChunkPos.pack(Mth.floor(px) >> 4, Mth.floor(pz) >> 4);
        double fx = -Mth.sin(facing), fz = Mth.cos(facing), rx = -fz, rz = fx;
        int n = 0;
        for (int k = WildKinds.BODIES; k < WildKinds.COUNT; k++) {
            int many = k == WildKinds.FIREFLY ? 8 : k == WildKinds.GNATS ? 1 : 3;
            for (int j = 0; j < many; j++) {
                int i = -1;
                for (int q = 0; q < CAP; q++) if (kind[q] < 0) {
                    i = q;
                    break;
                }
                if (i < 0) return n;
                double off = (k - WildKinds.BODIES - 3) * 1.4 + (j - many / 2.0) * 0.45;
                double d = 3.5 + (j % 3) * 1.1;
                double x0 = px + fx * d + rx * off, z0 = pz + fz * d + rz * off, g = Wildlife.ground(level, x0, z0);
                if (Double.isNaN(g)) continue;
                double y0 = g + (k == WildKinds.STRIDER ? 0.02 : k == WildKinds.FIREFLY ? 0.6 + (j % 4) * 0.4 : 1.0);
                kind[i] = k;
                home[i] = chunk;
                slot[i] = -1 - j; // the studio's: the director leaves them be
                state[i] = FLY;
                timer[i] = 20;
                variant[i] = j % 4;
                ax[i] = x[i] = xo[i] = tx[i] = x0;
                ay[i] = y[i] = yo[i] = ty[i] = y0;
                az[i] = z[i] = zo[i] = tz[i] = z0;
                vx[i] = vy[i] = vz[i] = 0;
                alpha[i] = alphaO[i] = 1F;
                yaw[i] = yawO[i] = R.nextFloat() * 360F;
                phase[i] = R.nextFloat() * Mth.TWO_PI;
                rate[i] = 0.85F + R.nextFloat() * 0.3F;
                leaving[i] = false;
                light[i] = LightCoordsUtil.FULL_BRIGHT;
                live++;
                n++;
            }
        }
        return n;
    }

    private static void free(int i) {
        kind[i] = -1;
        live--;
    }

    // ---- once a tick --------------------------------------------------------------------------------------------------

    static void tick(ClientLevel level) {
        if (flock && (Wildlife.now - fborn) * fspeed > 240) flock = false;
        if (live == 0) return;
        for (int i = 0; i < CAP; i++) {
            int k = kind[i];
            if (k < 0) continue;
            xo[i] = x[i];
            yo[i] = y[i];
            zo[i] = z[i];
            yawO[i] = yaw[i];
            alphaO[i] = alpha[i];
            double dpx = x[i] - Wildlife.px, dpz = z[i] - Wildlife.pz;
            double d2 = dpx * dpx + dpz * dpz;
            if (d2 > (WildKinds.REACH[k] + 12) * (WildKinds.REACH[k] + 12) || Sites.get(home[i]) == null) leaving[i] = true;
            alpha[i] = leaving[i] ? alpha[i] - 0.05F : Math.min(1F, alpha[i] + 0.04F);
            if (alpha[i] <= 0F && leaving[i]) {
                free(i);
                continue;
            }
            if ((Wildlife.now + i) % 10 == 0) {
                M.set(Mth.floor(x[i]), Mth.floor(y[i]), Mth.floor(z[i]));
                light[i] = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, M), level.getBrightness(LightLayer.SKY, M));
            }
            switch (k) {
                case WildKinds.BUTTERFLY, WildKinds.MOTH -> flutter(i, level, k == WildKinds.MOTH);
                case WildKinds.BEE -> bee(i, level);
                case WildKinds.DRAGONFLY -> dragonfly(i, level);
                case WildKinds.FIREFLY -> firefly(i);
                case WildKinds.GNATS -> {
                    y[i] = ay[i] + Math.sin((Wildlife.now + i * 13) * 0.05) * 0.4;
                    x[i] = ax[i] + Math.sin((Wildlife.now + i * 7) * 0.021) * 0.6;
                    z[i] = az[i] + Math.cos((Wildlife.now + i * 5) * 0.017) * 0.6;
                }
                case WildKinds.STRIDER -> strider(i, level);
                default -> free(i);
            }
        }
    }

    /** Toward the target with a lag, turning to face the way it goes. */
    private static void steer(int i, double speed, double agility) {
        double dx = tx[i] - x[i], dy = ty[i] - y[i], dz = tz[i] - z[i], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sp = Math.min(speed, d * 0.5);
        if (d > 1e-4) {
            vx[i] += (dx / d * sp - vx[i]) * agility;
            vy[i] += (dy / d * sp - vy[i]) * agility;
            vz[i] += (dz / d * sp - vz[i]) * agility;
        }
        x[i] += vx[i];
        y[i] += vy[i];
        z[i] += vz[i];
        if (vx[i] * vx[i] + vz[i] * vz[i] > 1e-6) {
            float want = (float) (Mth.atan2(vz[i], vx[i]) * Mth.RAD_TO_DEG) - 90F;
            yaw[i] += Mth.clamp(Mth.wrapDegrees(want - yaw[i]), -25F, 25F);
        }
    }

    private static boolean near(int i, double r) {
        double dx = x[i] - Wildlife.px, dy = y[i] - (Wildlife.py + 1), dz = z[i] - Wildlife.pz;
        return dx * dx + dy * dy + dz * dz < r * r;
    }

    /** A butterfly's (or a moth's) wandering: erratic, bobbing, now and then settling on a flower (or circling the lamp). */
    private static void flutter(int i, ClientLevel level, boolean moth) {
        if (state[i] == REST) {
            vx[i] = vy[i] = vz[i] = 0;
            if (--timer[i] <= 0 || near(i, 1.8)) {
                state[i] = FLY;
                timer[i] = 30 + R.nextInt(60);
                vy[i] = 0.06;
            }
            return;
        }
        if (--timer[i] <= 0) {
            timer[i] = moth ? 6 + R.nextInt(10) : 14 + R.nextInt(30);
            double a = R.nextDouble() * Math.PI * 2, r = moth ? 0.4 + R.nextDouble() * 1.0 : 0.8 + R.nextDouble() * 3;
            tx[i] = ax[i] + Math.cos(a) * r;
            tz[i] = az[i] + Math.sin(a) * r;
            ty[i] = ay[i] + (moth ? (R.nextDouble() - 0.5) * 1.2 : 0.2 + R.nextDouble() * 1.6);
            // settle on the flower it came from now and then
            if (!moth && R.nextFloat() < 0.25F) {
                tx[i] = ax[i];
                ty[i] = ay[i] - 0.15;
                tz[i] = az[i];
                timer[i] = 40;
                state[i] = DART; // on its way down
            }
        }
        if (near(i, 1.5) && !moth) {
            // away from your hand
            tx[i] = x[i] + (x[i] - Wildlife.px) * 2;
            tz[i] = z[i] + (z[i] - Wildlife.pz) * 2;
            ty[i] = y[i] + 1.2;
        }
        steer(i, moth ? 0.09 : 0.07, moth ? 0.35 : 0.18);
        // the flutter: little jinks up and down and aside
        y[i] += Math.sin(Wildlife.now * 0.9 * rate[i] + phase[i]) * 0.02;
        x[i] += (R.nextDouble() - 0.5) * (moth ? 0.05 : 0.025);
        z[i] += (R.nextDouble() - 0.5) * (moth ? 0.05 : 0.025);
        if (state[i] == DART && Math.abs(x[i] - ax[i]) < 0.12 && Math.abs(z[i] - az[i]) < 0.12 && Math.abs(y[i] - ty[i]) < 0.12) {
            state[i] = REST;
            timer[i] = 50 + R.nextInt(120);
            x[i] = ax[i];
            z[i] = az[i];
        }
    }

    /** A bee: straight, quick hops flower to flower in its chunk, hanging a moment at each. */
    private static void bee(int i, ClientLevel level) {
        if (state[i] == REST) {
            x[i] += (R.nextDouble() - 0.5) * 0.01;
            y[i] = ty[i] + Math.sin(Wildlife.now * 0.6 + phase[i]) * 0.02;
            if (--timer[i] <= 0) {
                state[i] = FLY;
                Sites.Site s = Sites.get(home[i]);
                long p = s != null && s.nFlower > 0 ? s.flower[R.nextInt(s.nFlower)] : 0L;
                if (p != 0L && R.nextFloat() < 0.8F) {
                    tx[i] = BlockPos.getX(p) + 0.5 + (R.nextDouble() - 0.5) * 0.3;
                    ty[i] = BlockPos.getY(p) + 0.55;
                    tz[i] = BlockPos.getZ(p) + 0.5 + (R.nextDouble() - 0.5) * 0.3;
                } else {
                    tx[i] = ax[i];
                    ty[i] = ay[i];
                    tz[i] = az[i];
                }
                timer[i] = 200;
            }
            return;
        }
        steer(i, 0.16, 0.3);
        y[i] += Math.sin(Wildlife.now * 0.7 + phase[i]) * 0.012;
        double dx = tx[i] - x[i], dy = ty[i] - y[i], dz = tz[i] - z[i];
        if (dx * dx + dy * dy + dz * dz < 0.02 || --timer[i] <= 0) {
            state[i] = REST;
            timer[i] = 25 + R.nextInt(50);
            vx[i] = vy[i] = vz[i] = 0;
            ty[i] = y[i];
        }
        if (near(i, 2.5) && Wildlife.now % 30 == i % 30) WildSound.play(x[i], y[i], z[i], SoundEvents.BEE_LOOP, 0.25F, 1.1F);
    }

    /** A dragonfly: hover, a dart, hover; low over its water. */
    private static void dragonfly(int i, ClientLevel level) {
        if (state[i] == FLY) {
            // hovering: tiny drifts
            x[i] += Math.sin(Wildlife.now * 0.3 + phase[i]) * 0.008;
            y[i] += Math.sin(Wildlife.now * 0.45 + phase[i]) * 0.006;
            if (--timer[i] <= 0 || near(i, 2.2)) {
                state[i] = DART;
                double a = R.nextDouble() * Math.PI * 2, r = 1.5 + R.nextDouble() * 4;
                tx[i] = ax[i] + Math.cos(a) * r;
                tz[i] = az[i] + Math.sin(a) * r;
                ty[i] = ay[i] + (R.nextDouble() - 0.4) * 0.8;
                if (!Birds.still(level, Mth.floor(tx[i]), Mth.floor(ay[i] - 1.3), Mth.floor(tz[i]))) {
                    tx[i] = ax[i];
                    tz[i] = az[i];
                }
                timer[i] = 30;
            }
            return;
        }
        steer(i, 0.38, 0.45);
        double dx = tx[i] - x[i], dz = tz[i] - z[i];
        if (dx * dx + dz * dz < 0.04 || --timer[i] <= 0) {
            state[i] = FLY;
            vx[i] = vy[i] = vz[i] = 0;
            timer[i] = 20 + R.nextInt(60);
        }
    }

    /** A firefly: a slow drift on the night air, rising and falling. Its blink is drawn from its phase. */
    private static void firefly(int i) {
        double t = (Wildlife.now + i * 31) * 0.01 * rate[i];
        tx[i] = ax[i] + Math.sin(t * 1.3 + phase[i]) * 1.6;
        tz[i] = az[i] + Math.cos(t * 1.1 + phase[i] * 0.7) * 1.6;
        ty[i] = ay[i] + Math.sin(t * 0.7 + phase[i]) * 0.6;
        steer(i, 0.025, 0.05);
        if (near(i, 1.2)) {
            vx[i] += (x[i] - Wildlife.px) * 0.01;
            vz[i] += (z[i] - Wildlife.pz) * 0.01;
        }
    }

    /** A water strider: still, then a skate of a few quick strokes, a ring at each. */
    private static void strider(int i, ClientLevel level) {
        if (--timer[i] > 0) {
            x[i] += vx[i];
            z[i] += vz[i];
            vx[i] *= 0.82;
            vz[i] *= 0.82;
            return;
        }
        timer[i] = 20 + R.nextInt(50);
        double a = R.nextDouble() * Math.PI * 2;
        if (near(i, 2.5)) a = Math.atan2(z[i] - Wildlife.pz, x[i] - Wildlife.px);
        double nx = x[i] + Math.cos(a) * 1.2, nz = z[i] + Math.sin(a) * 1.2;
        if (Math.hypot(nx - ax[i], nz - az[i]) > 4 || !Birds.still(level, Mth.floor(nx), Mth.floor(ay[i] - 0.9), Mth.floor(nz))) {
            a = Math.atan2(az[i] - z[i], ax[i] - x[i]);
        }
        vx[i] = Math.cos(a) * 0.12;
        vz[i] = Math.sin(a) * 0.12;
        yaw[i] = (float) (a * Mth.RAD_TO_DEG) - 90F;
        ripple(x[i], y[i] + 0.01, z[i], 0.6F);
    }
}
