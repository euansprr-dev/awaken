package dev.awaken.caves.client;

import dev.awaken.caves.CaveRegion;
import dev.awaken.caves.CavesModule;
import dev.awaken.caves.life.UnderSites;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.delve.api.Bands;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3fc;

import java.util.Arrays;

/**
 * The underlife (SPEC §42.5): the small lives of the caves, on the client only (never saved, never sent, nothing to hit),
 * simulated together in flat arrays (nothing allocated after start) and drawn by {@link UnderlifeRender} from the
 * underlife sheet. Moths come in out of the dark to your light, or a torch on the wall, and flutter round it; beetles and
 * millipedes walk the walls, floors and roofs, stopping when you come close; bats hang in a cluster from a high roof and
 * burst off it as you pass under, flitting about the passage until you've gone, then settling again; pale fish drift in
 * still pools and dart off when you lean over; crickets sit by the walls and chirp, falling quiet when you're near, and
 * hop away if you come closer. Every half second the director looks at a few cells round you for a floor, a wall, a high
 * roof, a pool or a light ({@link UnderSites}) and lets something live there; whatever you leave behind fades away.
 * Overworld caves only (the gates' worlds have their own life); quieter in the Middle Dark, nearly empty in the Deep.
 * The regions (SPEC §42.7) have their own: glow-worm threads hanging in clusters from the Rootways' roofs, glowing in the
 * dark; rust beetles on the Ochre Deep's walls, and its crickets the chime crickets.
 */
final class Underlife {
    private Underlife() {}

    static final int CAP = 40;
    static final int MOTH = 0, BEETLE = 1, MILLIPEDE = 2, BAT = 3, FISH = 4, CRICKET = 5, GLOWWORM = 6, KINDS = 7;
    /** At most this many of each kind at once (the pool holds 40; glow-worms only where bats and the rest are few). */
    static final int[] MAX = {6, 5, 3, 14, 6, 5, 10};
    static final String[] NAMES = {"moths", "beetles", "millipedes", "bats", "fish", "crickets", "glow-worm threads"};
    /** A region's own look or voice for a kind: 0 the plain one; 1 the Ochre Deep's rust beetle or chime cricket. */
    static final int RUST = 1, CHIME = 1;
    /** Glow-worm threads give their own light: full block light, so they shine in the dark. */
    static final int GLOW = LightCoordsUtil.pack(15, 0);
    static final int MOVE = 0, REST = 1, FLY = 2, HOP = 3;
    /** A moth's anchor that follows the hand instead of staying put. */
    static final int HAND = -2;
    static final int REACH = 22, LEAVE = 28;

    // the pool: kind < 0 is a free slot
    static final int[] kind = new int[CAP], state = new int[CAP], timer = new int[CAP], light = new int[CAP], face = new int[CAP],
            group = new int[CAP], blockLight = new int[CAP], chirp = new int[CAP], age = new int[CAP], variant = new int[CAP];
    static final double[] x = new double[CAP], y = new double[CAP], z = new double[CAP], xo = new double[CAP], yo = new double[CAP],
            zo = new double[CAP], vx = new double[CAP], vy = new double[CAP], vz = new double[CAP], ax = new double[CAP],
            ay = new double[CAP], az = new double[CAP], tx = new double[CAP], ty = new double[CAP], tz = new double[CAP],
            sx = new double[CAP], sy = new double[CAP], sz = new double[CAP];
    static final float[] alpha = new float[CAP], alphaO = new float[CAP], yaw = new float[CAP], yawO = new float[CAP],
            phase = new float[CAP], rate = new float[CAP];
    static final boolean[] leaving = new boolean[CAP], held = new boolean[CAP];
    static final int[] count = new int[KINDS];
    private static int live, groups;

    // this tick: the player, their hand and its light, the camera
    static double px, py, pz, handX, handY, handZ, camX, camY, camZ;
    static float fwdX, fwdY, fwdZ;
    static int heldLight;
    static long now;
    static boolean enabled = true, under;
    private static Bands.Band band = Bands.Band.SHALLOWS;
    private static CaveRegion region;
    private static long nextVoice;

    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    static {
        Arrays.fill(kind, -1);
    }

    static int count() {
        return live;
    }

    static void clear() {
        Arrays.fill(kind, -1);
        Arrays.fill(count, 0);
        live = 0;
    }

    // ---- once a tick ------------------------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null || mc.isPaused()) return;
        now = level.getGameTime();
        px = p.getX();
        py = p.getY();
        pz = p.getZ();
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 cp = cam.position();
        camX = cp.x;
        camY = cp.y;
        camZ = cp.z;
        Vector3fc f = cam.forwardVector();
        fwdX = f.x();
        fwdY = f.y();
        fwdZ = f.z();
        heldLight = Math.max(UnderSites.heldLight(p.getMainHandItem()), UnderSites.heldLight(p.getOffhandItem()));
        // the hand: a little in front, to the right and below the eye
        Vec3 look = p.getViewVector(1F);
        handX = p.getX() + look.x * 0.45 - look.z * 0.3;
        handY = p.getEyeY() - 0.35 + look.y * 0.45;
        handZ = p.getZ() + look.z * 0.45 + look.x * 0.3;
        if (now % 10 == 0) {
            under = underground(level, p);
            band = Bands.of(Mth.floor(py));
            region = RegionClient.here;
        }
        boolean on = enabled && CavesClientConfig.underlife() && under && level.dimension() == Level.OVERWORLD;
        if (live > 0) step(level, on);
        if (on && now % 10 == 0) populate(level);
    }

    /** Under the rock: no sky light where you stand, and the land's top well above you. */
    private static boolean underground(ClientLevel level, LocalPlayer p) {
        int bx = Mth.floor(p.getX()), by = Mth.floor(p.getEyeY()), bz = Mth.floor(p.getZ());
        if (level.getBrightness(LightLayer.SKY, M.set(bx, by, bz)) > 2) return false;
        return by < level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 5;
    }

    private static void step(ClientLevel level, boolean on) {
        for (int i = 0; i < CAP; i++) {
            int k = kind[i];
            if (k < 0) continue;
            xo[i] = x[i];
            yo[i] = y[i];
            zo[i] = z[i];
            yawO[i] = yaw[i];
            alphaO[i] = alpha[i];
            if (!held[i]) {
                double dx = x[i] - px, dy = y[i] - py, dz = z[i] - pz;
                if (!on || dx * dx + dy * dy + dz * dz > LEAVE * LEAVE || k == MOTH && face[i] == HAND && heldLight == 0) leaving[i] = true;
            }
            alpha[i] = leaving[i] ? alpha[i] - 0.05F : Math.min(1F, alpha[i] + 0.04F);
            if (alpha[i] <= 0F && leaving[i]) {
                free(i);
                continue;
            }
            if ((now + i) % 10 == 0) blockLight[i] = level.getBrightness(LightLayer.BLOCK, M.set(Mth.floor(x[i]), Mth.floor(y[i]), Mth.floor(z[i])));
            light[i] = k == GLOWWORM ? GLOW : LightCoordsUtil.pack(Math.max(blockLight[i], heldAt(x[i], y[i], z[i])), 0);
            if (held[i]) {
                if (k == MOTH || k == BAT && state[i] == FLY) yaw[i] += 2F;
                continue;
            }
            switch (k) {
                case MOTH -> moth(i);
                case BEETLE, MILLIPEDE -> crawl(i, level);
                case BAT -> bat(i, level);
                case FISH -> fish(i, level);
                case CRICKET -> cricket(i, level);
                case GLOWWORM -> { }   // they hang where they are
                default -> free(i);
            }
        }
    }

    /** The block light a held light gives at a point: its level less the distance, eased out over its reach. */
    static int heldAt(double x, double y, double z) {
        if (heldLight == 0) return 0;
        double dx = x - handX, dy = y - handY, dz = z - handZ;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz), r = heldLight * 0.75F;
        if (d >= r) return 0;
        float fade = Mth.clamp((r - d) / (r * 0.4F), 0F, 1F);
        return Math.min(15, (int) ((heldLight - d) * fade * fade * (3 - 2 * fade) + 0.5F));
    }

    // ---- who lives here (the half-second pass) ------------------------------------------------------------------------

    /** How many of a kind this band holds: the Shallows all, the Middle Dark fewer bats and moths, the Deep a few beetles and fish. */
    private static int want(int k) {
        if (k == GLOWWORM) return region == CaveRegion.ROOTWAYS ? MAX[k] : 0;
        return switch (band) {
            case SHALLOWS -> MAX[k];
            case MIDDLE -> k == BAT ? 7 : k == MOTH ? 4 : MAX[k];
            case DEEP -> k == BEETLE ? 3 : k == FISH ? 3 : 0;
        };
    }

    private static void populate(ClientLevel level) {
        int made = 0;
        for (int s = 0; s < 28 && made < 2; s++) {
            int dx = R.nextInt(2 * REACH + 1) - REACH, dy = R.nextInt(21) - 10, dz = R.nextInt(2 * REACH + 1) - REACH;
            int d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < 36 || d2 > REACH * REACH) continue;
            int cx = Mth.floor(px) + dx, cy = Mth.floor(py) + dy, cz = Mth.floor(pz) + dz;
            if (!level.hasChunk(cx >> 4, cz >> 4)) continue;
            // in plain sight close by nothing appears (moths fly in from the dark, so they may)
            boolean seen = d2 < 14 * 14 && inView(cx + 0.5, cy + 0.5, cz + 0.5, 0.45F);
            if (count[MOTH] < Math.min(want(MOTH), 2 + heldLight / 4) && heldLight > 0 && d2 < 12 * 12
                    && UnderSites.open(level.getBlockState(M.set(cx, cy, cz)))) {
                if (moth(cx + 0.5, cy + 0.5, cz + 0.5, HAND, 0, 0, 0)) made++;
                continue;
            }
            int bits = UnderSites.classify(level, M, cx, cy, cz);
            if (bits == 0) {
                lamp(level, cx, cy, cz);
                continue;
            }
            if (seen) continue;
            if ((bits & UnderSites.POOL) != 0) {
                if (count[FISH] < want(FISH) && fishAt(level, cx, cy, cz)) made++;
            } else if ((bits & UnderSites.ROOF) != 0 && count[GLOWWORM] + 2 <= want(GLOWWORM) && R.nextFloat() < 0.5F) {
                if (glowworms(level, cx, cy, cz)) made++;
            } else if ((bits & UnderSites.ROOST) != 0 && count[BAT] + 4 <= want(BAT) && d2 > 10 * 10) {
                if (roost(level, cx, cy, cz)) made++;
            } else if ((bits & UnderSites.WALL) != 0 && count[BEETLE] < want(BEETLE) && R.nextFloat() < 0.6F) {
                Direction wall = UnderSites.wall(level, M, cx, cy, cz);
                if (wall != null && crawler(BEETLE, cx, cy, cz, wall.getOpposite())) made++;
            } else if ((bits & UnderSites.FLOOR) != 0 && (bits & UnderSites.WALL) != 0 && count[CRICKET] < want(CRICKET)) {
                if (cricket(cx, cy, cz)) made++;
            } else if ((bits & (UnderSites.FLOOR | UnderSites.WALL)) != 0 && count[MILLIPEDE] < want(MILLIPEDE) && R.nextFloat() < 0.4F) {
                Direction wall = (bits & UnderSites.WALL) != 0 && R.nextBoolean() ? UnderSites.wall(level, M, cx, cy, cz) : null;
                if (crawler(MILLIPEDE, cx, cy, cz, wall != null ? wall.getOpposite() : Direction.UP)) made++;
            } else if ((bits & UnderSites.ROOF) != 0 && count[BEETLE] < want(BEETLE) && R.nextFloat() < 0.3F) {
                if (crawler(BEETLE, cx, cy, cz, Direction.DOWN)) made++;
            }
        }
    }

    /** A torch, lantern or any bright light on the walls: two or three moths come to it, if none are there yet. */
    private static void lamp(ClientLevel level, int cx, int cy, int cz) {
        if (count[MOTH] + 2 > want(MOTH) || level.getBrightness(LightLayer.BLOCK, M.set(cx, cy, cz)) < 13) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (level.getBlockState(M.set(cx + dx, cy + dy, cz + dz)).getLightEmission() < UnderSites.MOTH_LIGHT) continue;
                    double lx = cx + dx + 0.5, ly = cy + dy + 0.5, lz = cz + dz + 0.5;
                    for (int i = 0; i < CAP; i++) {
                        if (kind[i] == MOTH && face[i] != HAND && Math.abs(ax[i] - lx) < 1 && Math.abs(ay[i] - ly) < 1 && Math.abs(az[i] - lz) < 1) return;
                    }
                    int n = 2 + R.nextInt(2);
                    for (int j = 0; j < n; j++) {
                        double a = R.nextDouble() * Math.PI * 2;
                        moth(lx + Math.cos(a) * 3, ly + 0.5, lz + Math.sin(a) * 3, 0, lx, ly, lz);
                    }
                    return;
                }
            }
        }
    }

    private static int slot(int k) {
        if (count[k] >= MAX[k]) return -1;
        for (int i = 0; i < CAP; i++) {
            if (kind[i] < 0) {
                kind[i] = k;
                count[k]++;
                live++;
                state[i] = MOVE;
                timer[i] = 20 + R.nextInt(40);
                alpha[i] = alphaO[i] = 0F;
                yaw[i] = yawO[i] = R.nextFloat() * 360F;
                phase[i] = R.nextFloat() * Mth.TWO_PI;
                rate[i] = 0.85F + R.nextFloat() * 0.3F;
                leaving[i] = held[i] = false;
                vx[i] = vy[i] = vz[i] = 0;
                face[i] = 0;
                group[i] = -1;
                chirp[i] = 40 + R.nextInt(200);
                blockLight[i] = 0;
                light[i] = 0;
                age[i] = 0;
                variant[i] = region == CaveRegion.OCHRE_DEEP && (k == BEETLE || k == CRICKET) ? RUST : 0;
                return i;
            }
        }
        return -1;
    }

    private static void place(int i, double nx, double ny, double nz) {
        x[i] = xo[i] = tx[i] = nx;
        y[i] = yo[i] = ty[i] = ny;
        z[i] = zo[i] = tz[i] = nz;
    }

    private static void free(int i) {
        count[kind[i]]--;
        kind[i] = -1;
        live--;
    }

    // ---- moths: in out of the dark, round and round the light ---------------------------------------------------------

    private static boolean moth(double sx0, double sy0, double sz0, int anchor, double lx, double ly, double lz) {
        int i = slot(MOTH);
        if (i < 0) return false;
        place(i, sx0, sy0, sz0);
        face[i] = anchor;
        ax[i] = anchor == HAND ? handX : lx;
        ay[i] = anchor == HAND ? handY : ly;
        az[i] = anchor == HAND ? handZ : lz;
        timer[i] = 0;
        return true;
    }

    private static void moth(int i) {
        if (face[i] == HAND) {
            ax[i] = handX;
            ay[i] = handY;
            az[i] = handZ;
        }
        if (--timer[i] <= 0) {
            timer[i] = 5 + R.nextInt(10);
            // round the light, now and then straight into it (a bump, and off again)
            double a = R.nextDouble() * Math.PI * 2, r = R.nextFloat() < 0.15F ? 0.05 : 0.35 + R.nextDouble() * 0.9;
            tx[i] = ax[i] + Math.cos(a) * r;
            tz[i] = az[i] + Math.sin(a) * r;
            ty[i] = ay[i] + (R.nextDouble() - 0.35) * 0.9;
        }
        steer(i, 0.11, 0.32);
        y[i] += Math.sin(now * 0.9 * rate[i] + phase[i]) * 0.02;
        x[i] += (R.nextDouble() - 0.5) * 0.05;
        z[i] += (R.nextDouble() - 0.5) * 0.05;
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

    // ---- beetles and millipedes: walking a surface ----------------------------------------------------------------------

    /**
     * A crawler on the face of the rock at cell + (opposite of {@code out}): {@code face} is the way out of the rock, its
     * yaw the heading within that face (0 along the face's first axis, see {@link #axes}).
     */
    private static boolean crawler(int k, int cx, int cy, int cz, Direction out) {
        int i = slot(k);
        if (i < 0) return false;
        double ox = cx + 0.5 - out.getStepX() * 0.49 + (out.getStepX() == 0 ? (R.nextDouble() - 0.5) * 0.7 : 0);
        double oy = cy + 0.5 - out.getStepY() * 0.49 + (out.getStepY() == 0 ? (R.nextDouble() - 0.5) * 0.7 : 0);
        double oz = cz + 0.5 - out.getStepZ() * 0.49 + (out.getStepZ() == 0 ? (R.nextDouble() - 0.5) * 0.7 : 0);
        place(i, ox, oy, oz);
        ax[i] = ox;
        ay[i] = oy;
        az[i] = oz;
        face[i] = out.ordinal();
        state[i] = R.nextBoolean() ? MOVE : REST;
        return true;
    }

    /** A face's two in-plane axes (u then v) for a surface facing {@code out}. */
    static void axes(Direction out, double[] uv) {
        switch (out.getAxis()) {
            case Y -> { uv[0] = 1; uv[1] = 0; uv[2] = 0; uv[3] = 0; uv[4] = 0; uv[5] = 1; }
            case Z -> { uv[0] = 1; uv[1] = 0; uv[2] = 0; uv[3] = 0; uv[4] = 1; uv[5] = 0; }
            case X -> { uv[0] = 0; uv[1] = 0; uv[2] = 1; uv[3] = 0; uv[4] = 1; uv[5] = 0; }
        }
    }

    private static final double[] UV = new double[6];
    private static final Direction[] DIRS = Direction.values();

    private static void crawl(int i, ClientLevel level) {
        boolean beetle = kind[i] == BEETLE;
        double dpx = x[i] - px, dpy = y[i] - (py + 0.5), dpz = z[i] - pz;
        // still while you're close: a beetle freezes, a millipede curls up and waits
        if (dpx * dpx + dpy * dpy + dpz * dpz < 2.2 * 2.2) {
            state[i] = REST;
            timer[i] = Math.max(timer[i], 30);
        }
        if (--timer[i] <= 0) {
            state[i] = state[i] == MOVE ? REST : MOVE;
            timer[i] = state[i] == MOVE ? 30 + R.nextInt(beetle ? 90 : 160) : 20 + R.nextInt(beetle ? 70 : 120);
            if (state[i] == MOVE) yaw[i] += (R.nextFloat() - 0.5F) * 120F;
        }
        if (state[i] != MOVE) return;
        Direction out = DIRS[face[i]];
        axes(out, UV);
        yaw[i] += (R.nextFloat() - 0.5F) * (beetle ? 14F : 8F);
        float a = yaw[i] * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a);
        double sp = beetle ? 0.028 : 0.013;
        double nx = x[i] + (UV[0] * c + UV[3] * s) * sp, ny = y[i] + (UV[1] * c + UV[4] * s) * sp, nz = z[i] + (UV[2] * c + UV[5] * s) * sp;
        double hx = nx - ax[i], hy = ny - ay[i], hz = nz - az[i];
        if (hx * hx + hy * hy + hz * hz < 16 && UnderSites.onSurface(level, M, nx, ny, nz, out)) {
            x[i] = nx;
            y[i] = ny;
            z[i] = nz;
        } else {
            // the edge of its face, or too far from home: turn about
            yaw[i] += 100F + R.nextFloat() * 160F;
            yawO[i] = yaw[i];
        }
    }

    // ---- bats: a roost on a high roof that bursts off as you pass -------------------------------------------------------

    private static boolean roost(ClientLevel level, int cx, int cy, int cz) {
        // not two roosts in one place
        for (int i = 0; i < CAP; i++) if (kind[i] == BAT && Math.abs(ax[i] - cx) < 12 && Math.abs(az[i] - cz) < 12 && Math.abs(ay[i] - cy) < 8) return false;
        int n = 4 + R.nextInt(5), made = 0, g = groups++;
        for (int t = 0; t < n * 3 && made < n; t++) {
            int hx = cx + R.nextInt(5) - 2, hz = cz + R.nextInt(5) - 2;
            for (int hy = cy + 2; hy >= cy - 2; hy--) {
                if (!UnderSites.open(level.getBlockState(M.set(hx, hy, hz))) || !UnderSites.rock(level.getBlockState(M.set(hx, hy + 1, hz)))) continue;
                if (crowded(hx, hy, hz)) break;
                int i = slot(BAT);
                if (i < 0) return made > 0;
                double bx = hx + 0.25 + R.nextDouble() * 0.5, by = hy + 1 - 0.02, bz = hz + 0.25 + R.nextDouble() * 0.5;
                place(i, bx, by, bz);
                ax[i] = bx;
                ay[i] = by;
                az[i] = bz;
                group[i] = g;
                state[i] = REST;
                made++;
                break;
            }
        }
        return made > 0;
    }

    private static boolean crowded(int hx, int hy, int hz) {
        for (int i = 0; i < CAP; i++) if (kind[i] == BAT && Mth.floor(ax[i]) == hx && Mth.floor(ay[i] - 0.5) == hy && Mth.floor(az[i]) == hz) return true;
        return false;
    }

    private static void bat(int i, ClientLevel level) {
        double dpx = ax[i] - px, dpy = ay[i] - (py + 1), dpz = az[i] - pz, d2 = dpx * dpx + dpy * dpy + dpz * dpz;
        if (state[i] == REST) {
            // hanging: a slow sway, a shuffle of the wings now and then
            yaw[i] += Mth.sin(now * 0.03F + phase[i]) * 0.6F;
            boolean lit = heldLight > 0 && d2 < 9 * 9;
            if (d2 < 6.5 * 6.5 || lit && d2 < 7.5 * 7.5) scatter(group[i], level);
            return;
        }
        if (state[i] == HOP) {
            // the moment it lets go: a short drop before the wings catch
            vy[i] -= 0.02;
            y[i] += vy[i];
            if (--timer[i] <= 0) {
                state[i] = FLY;
                timer[i] = 100 + R.nextInt(140);
                pickFlight(i, level);
            }
            return;
        }
        // flying: flit from point to point round the roost, away from you; home when you've gone
        double dx = tx[i] - x[i], dy = ty[i] - y[i], dz = tz[i] - z[i];
        boolean home = tx[i] == ax[i] && tz[i] == az[i];
        if (home && dx * dx + dy * dy + dz * dz < 0.04) {
            state[i] = REST;
            place(i, ax[i], ay[i], az[i]);
            vx[i] = vy[i] = vz[i] = 0;
            return;
        }
        if (--timer[i] <= 0 || !home && dx * dx + dy * dy + dz * dz < 0.3) {
            if (d2 > 10 * 10 && --chirp[i] < 0) {
                tx[i] = ax[i];
                ty[i] = ay[i] - 0.3;
                tz[i] = az[i];
                timer[i] = 200;
            } else {
                pickFlight(i, level);
                timer[i] = 15 + R.nextInt(25);
            }
        }
        steer(i, 0.26, 0.28);
        y[i] += Math.sin(now * 0.7 + phase[i]) * 0.03;
        // half a minute about with you still there: off down the passage for good
        if (++age[i] > 600 && d2 < 10 * 10 && R.nextFloat() < 0.01F) leaving[i] = true;
        M.set(Mth.floor(x[i]), Mth.floor(y[i]), Mth.floor(z[i]));
        if (!UnderSites.open(level.getBlockState(M))) {
            // into rock: back where it was, a new way
            x[i] = xo[i];
            y[i] = yo[i];
            z[i] = zo[i];
            vx[i] = vy[i] = vz[i] = 0;
            pickFlight(i, level);
        }
    }

    /** The whole roost lets go, one after another, with one flurry of wings. */
    private static void scatter(int g, ClientLevel level) {
        boolean first = true;
        for (int j = 0; j < CAP; j++) {
            if (kind[j] != BAT || group[j] != g || state[j] != REST) continue;
            state[j] = HOP;
            timer[j] = 2 + R.nextInt(10);
            vy[j] = 0;
            chirp[j] = 2 + R.nextInt(3);      // how many flits it takes before it heads home once you've gone
            age[j] = 0;
            if (first) {
                voice(x[j], y[j], z[j], CavesModule.BATS.get(), 0.8F, 0.9F + R.nextFloat() * 0.2F, 0);
                first = false;
            }
        }
    }

    private static void pickFlight(int i, ClientLevel level) {
        for (int t = 0; t < 6; t++) {
            double a = R.nextDouble() * Math.PI * 2, r = 2 + R.nextDouble() * 5;
            double nx = ax[i] + Math.cos(a) * r, ny = ay[i] - 0.8 - R.nextDouble() * 3.5, nz = az[i] + Math.sin(a) * r;
            double qx = nx - px, qz = nz - pz;
            if (qx * qx + qz * qz < 9) continue;
            if (!UnderSites.open(level.getBlockState(M.set(Mth.floor(nx), Mth.floor(ny), Mth.floor(nz))))) continue;
            if (!UnderSites.open(level.getBlockState(M.set(Mth.floor((nx + x[i]) / 2), Mth.floor((ny + y[i]) / 2), Mth.floor((nz + z[i]) / 2))))) continue;
            tx[i] = nx;
            ty[i] = ny;
            tz[i] = nz;
            return;
        }
        tx[i] = x[i] + (R.nextDouble() - 0.5);
        ty[i] = y[i] - 0.3;
        tz[i] = z[i] + (R.nextDouble() - 0.5);
    }

    // ---- fish: a pale drift in still water --------------------------------------------------------------------------------

    private static boolean fishAt(ClientLevel level, int cx, int cy, int cz) {
        int made = 0, n = 1 + R.nextInt(3);
        for (int j = 0; j < n; j++) {
            int i = slot(FISH);
            if (i < 0) break;
            double fx = cx + 0.3 + R.nextDouble() * 0.4, fz = cz + 0.3 + R.nextDouble() * 0.4, fy = cy + 0.7 - R.nextDouble() * 0.12;
            place(i, fx, fy, fz);
            ax[i] = cx + 0.5;
            ay[i] = fy;
            az[i] = cz + 0.5;
            timer[i] = R.nextInt(30);
            made++;
        }
        return made > 0;
    }

    private static void fish(int i, ClientLevel level) {
        double dpx = x[i] - px, dpz = z[i] - pz;
        boolean startled = dpx * dpx + dpz * dpz < 3.2 * 3.2;
        if (--timer[i] <= 0 || startled && state[i] != HOP) {
            for (int t = 0; t < 5; t++) {
                double a = startled ? Math.atan2(dpz, dpx) + (R.nextDouble() - 0.5) : R.nextDouble() * Math.PI * 2;
                double r = startled ? 1.5 + R.nextDouble() * 1.5 : 0.4 + R.nextDouble() * 2.2;
                double nx = x[i] + Math.cos(a) * r, nz = z[i] + Math.sin(a) * r;
                if ((nx - ax[i]) * (nx - ax[i]) + (nz - az[i]) * (nz - az[i]) > 4 * 4) continue;
                if (!UnderSites.water(level.getBlockState(M.set(Mth.floor(nx), Mth.floor(ay[i]), Mth.floor(nz))))) continue;
                tx[i] = nx;
                tz[i] = nz;
                break;
            }
            ty[i] = ay[i];
            state[i] = startled ? HOP : MOVE;
            timer[i] = startled ? 20 : 40 + R.nextInt(80);
        }
        steer(i, state[i] == HOP ? 0.14 : 0.022, state[i] == HOP ? 0.5 : 0.08);
        if (!UnderSites.water(level.getBlockState(M.set(Mth.floor(x[i]), Mth.floor(y[i]), Mth.floor(z[i]))))) {
            x[i] = xo[i];
            z[i] = zo[i];
            vx[i] = vz[i] = 0;
            timer[i] = 0;
        }
        if (state[i] == HOP && timer[i] < 8) state[i] = MOVE;
    }

    // ---- crickets: heard more than seen ---------------------------------------------------------------------------------

    private static boolean cricket(int cx, int cy, int cz) {
        int i = slot(CRICKET);
        if (i < 0) return false;
        double fx = cx + 0.25 + R.nextDouble() * 0.5, fz = cz + 0.25 + R.nextDouble() * 0.5;
        place(i, fx, cy + 0.01, fz);
        ax[i] = fx;
        ay[i] = cy + 0.01;
        az[i] = fz;
        state[i] = REST;
        timer[i] = 200 + R.nextInt(300);
        return true;
    }

    private static void cricket(int i, ClientLevel level) {
        double dpx = x[i] - px, dpy = y[i] - py, dpz = z[i] - pz, d2 = dpx * dpx + dpy * dpy + dpz * dpz;
        if (state[i] == HOP) {
            // a parabola from where it was to where it lands, ten ticks
            float t = 1F - timer[i] / 10F;
            x[i] = Mth.lerp(t, sx[i], tx[i]);
            z[i] = Mth.lerp(t, sz[i], tz[i]);
            y[i] = Mth.lerp(t, sy[i], ty[i]) + 4 * 0.7 * t * (1 - t);
            if (--timer[i] <= 0) {
                state[i] = REST;
                place(i, tx[i], ty[i], tz[i]);
                timer[i] = 200 + R.nextInt(400);
            }
            return;
        }
        if (d2 < 3.2 * 3.2 || --timer[i] <= 0) {
            hop(i, level, d2 < 3.2 * 3.2);
            return;
        }
        // its song: quiet while you're close, on and off from further away
        if (--chirp[i] <= 0) {
            chirp[i] = 100 + R.nextInt(220);
            if (d2 > 5 * 5 && d2 < REACH * REACH) voice(x[i], y[i] + 0.1, z[i], variant[i] == CHIME ? CavesModule.CHIME_CRICKET.get()
                    : CavesModule.CRICKET.get(), 0.7F, 0.92F + R.nextFloat() * 0.16F, 40);
        }
    }

    private static void hop(int i, ClientLevel level, boolean away) {
        double a = away ? Math.atan2(z[i] - pz, x[i] - px) + (R.nextDouble() - 0.5) * 1.2 : R.nextDouble() * Math.PI * 2;
        for (int t = 0; t < 6; t++) {
            double r = 1.2 + R.nextDouble() * 1.6;
            int nx = Mth.floor(x[i] + Math.cos(a) * r), nz = Mth.floor(z[i] + Math.sin(a) * r);
            for (int dy = 1; dy >= -2; dy--) {
                int ny = Mth.floor(y[i]) + dy;
                if (!UnderSites.open(level.getBlockState(M.set(nx, ny, nz))) || !UnderSites.rock(level.getBlockState(M.set(nx, ny - 1, nz)))) continue;
                sx[i] = x[i];
                sy[i] = y[i];
                sz[i] = z[i];
                tx[i] = nx + 0.25 + R.nextDouble() * 0.5;
                ty[i] = ny + 0.01;
                tz[i] = nz + 0.25 + R.nextDouble() * 0.5;
                yaw[i] = yawO[i] = (float) (Mth.atan2(tz[i] - z[i], tx[i] - x[i]) * Mth.RAD_TO_DEG) - 90F;
                state[i] = HOP;
                timer[i] = 10;
                return;
            }
            a += 0.9;
        }
        timer[i] = 100 + R.nextInt(200);
    }

    // ---- glow-worm threads: a cluster of two to four hanging from a roof ----------------------------------------------------

    private static boolean glowworms(ClientLevel level, int cx, int cy, int cz) {
        int n = 2 + R.nextInt(3), made = 0;
        for (int t = 0; t < 8 && made < n; t++) {
            int nx = cx + R.nextInt(3) - 1, nz = cz + R.nextInt(3) - 1;
            if (!UnderSites.open(level.getBlockState(M.set(nx, cy, nz))) || !UnderSites.rock(level.getBlockState(M.set(nx, cy + 1, nz)))) continue;
            int i = slot(GLOWWORM);
            if (i < 0) break;
            place(i, nx + 0.2 + R.nextDouble() * 0.6, cy + 0.995, nz + 0.2 + R.nextDouble() * 0.6);
            state[i] = REST;
            light[i] = GLOW;
            made++;
        }
        return made > 0;
    }

    // ---- voices -------------------------------------------------------------------------------------------------------

    /** A voice placed at a creature: quieter where the world is hushed; one new voice a second at most. */
    private static void voice(double vx0, double vy0, double vz0, SoundEvent event, float volume, float pitch, int gap) {
        if (now < nextVoice) return;
        float hush = Soundscape.hushAt(vx0, vy0, vz0);
        if (hush < 0.25F) return;
        nextVoice = now + 20 + gap;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, volume * hush, pitch, R, vx0, vy0, vz0));
    }

    static boolean inView(double x, double y, double z, float cos) {
        double dx = x - camX, dy = y - camY, dz = z - camZ, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-3) return true;
        return (dx * fwdX + dy * fwdY + dz * fwdZ) / l > cos;
    }

    // ---- tours ----------------------------------------------------------------------------------------------------------

    /** What's about, by kind. */
    static String counts() {
        StringBuilder b = new StringBuilder(under ? "under the rock (" + band.title + ")" : "not under the rock");
        for (int k = 0; k < KINDS; k++) b.append(", ").append(count[k]).append(' ').append(NAMES[k]);
        return b.toString();
    }

    /**
     * The studio: one or two of each kind held still in a row 2-4 blocks before the camera (moths and a bat flapping in
     * place, a roosting bat, the crawlers flat on the ground). They stay until cleared or the underlife is turned off.
     */
    static int lineup(ClientLevel level) {
        clear();
        double fx = -Mth.sin(yawOf()), fz = Mth.cos(yawOf()), rx = -fz, rz = fx;
        int gx = Mth.floor(px), gz = Mth.floor(pz), n = 0;
        double ground = py;
        int[] kinds = {MOTH, MOTH, BAT, BAT, BEETLE, BEETLE, MILLIPEDE, FISH, CRICKET, GLOWWORM};
        for (int j = 0; j < kinds.length; j++) {
            int k = kinds[j], i = slot(k);
            if (i < 0) continue;
            double off = (j - kinds.length / 2.0) * 0.55, d = 2.6;
            double bx = px + fx * d + rx * off, bz = pz + fz * d + rz * off;
            variant[i] = j == 5 ? RUST : 0;   // the second beetle is the Ochre Deep's
            double by = switch (k) {
                case MOTH -> ground + 1.4;
                case GLOWWORM -> ground + 2.2;
                case BAT -> ground + (j == 2 ? 1.9 : 1.2);
                case FISH -> ground + 0.05;
                default -> ground + 0.02;
            };
            place(i, bx, by, bz);
            ax[i] = bx;
            ay[i] = by;
            az[i] = bz;
            held[i] = true;
            alpha[i] = alphaO[i] = 1F;
            face[i] = Direction.UP.ordinal();
            state[i] = k == BAT && j == 3 || k == MOTH ? FLY : REST;
            yaw[i] = yawO[i] = (float) (yawOf() * Mth.RAD_TO_DEG) + 180F;
            n++;
        }
        return n;
    }

    private static float yawOf() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0F : p.getYRot() * Mth.DEG_TO_RAD;
    }
}
