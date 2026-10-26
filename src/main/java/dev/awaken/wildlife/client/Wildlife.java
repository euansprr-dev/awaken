package dev.awaken.wildlife.client;

import dev.awaken.Awaken;
import dev.awaken.citizens.client.CityLife;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.wildlife.Census;
import dev.awaken.wildlife.Density;
import dev.awaken.wildlife.WildCritter;
import dev.awaken.wildlife.WildKinds;
import dev.awaken.wildlife.WildlifeModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The wild's director (SPEC "Wild life"): who lives where near the player, made and dropped on the client only.
 * <p>Every half second it walks the chunks read by {@link Sites}, nearest first, asks {@link Density} how many of each
 * kind a chunk holds now (by its family, the hour, the weather and what it offers) and makes or retires animals to match,
 * each on its own spot from the chunk's fixed draw (so a place looks alike when you come back). New animals appear only
 * out of sight or far enough off to be specks; ones no longer wanted leave the way they would (a bird flies off, a rabbit
 * runs for cover) and are dropped out of sight. Never inside Carillon's walls (its animals are B3b's), never in Awaken's
 * own dimensions. The brains ({@link Birds}, {@link Critters}) run on each animal's tick; insects are {@link Insects}.
 */
public final class Wildlife {
    private Wildlife() {}

    /** The player and the hour, read once a tick for every brain. */
    static double px, py, pz, pSpeed;
    static boolean sprinting, sneaking;
    static long now;
    static int tod;
    static float rain, thunder;
    static double camX, camY, camZ;
    static float fwdX, fwdY = 0, fwdZ = 1;
    static boolean enabled = true;
    /** A density dial for tours and taste (1 = the tables as written). */
    static float dial = 1F;

    static final List<WildCritter> LIVE = new ArrayList<>();
    static final Census CENSUS = new Census();
    private static int nextId = -1_000_000_000, clock;
    private static final boolean AUTOPILOT = System.getProperty("awaken.autopilot") != null;
    /** Clip indices per kind, by name (the rig's clip order is the model's). */
    static final Map<String, Integer>[] CLIPS = clipTables();
    private static Sites.Site[] order = new Sites.Site[64];
    private static long[] orderKey = new long[64];

    @SuppressWarnings("unchecked")
    private static Map<String, Integer>[] clipTables() {
        Map<String, Integer>[] t = new Map[WildKinds.BODIES];
        for (int k = 0; k < t.length; k++) {
            t[k] = new HashMap<>();
            HeroRig rig = WildRenderer.rigs().get(WildKinds.RIGS[k]);
            int i = 0;
            for (String name : rig.clips.keySet()) t[k].put(name, i++);
        }
        return t;
    }

    static void init() {
        WildCritter.brain = Wildlife::think;
    }

    static void clear() {
        for (WildCritter c : LIVE) c.discard();
        LIVE.clear();
        Sites.clear();
        Insects.clear();
    }

    /** Off in Awaken's own dimensions (gates, strata, dreams, the Halo) and the Nether and the End. */
    static boolean wildWorld(ClientLevel level) {
        return level.dimension() == Level.OVERWORLD;
    }

    // ---- once a tick -----------------------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null || mc.isPaused()) return;
        if (!enabled || !wildWorld(level)) {
            if (!LIVE.isEmpty() || Insects.count() > 0) clear();
            return;
        }
        double npx = p.getX(), npz = p.getZ();
        pSpeed = Math.hypot(npx - px, npz - pz);
        px = npx;
        py = p.getY();
        pz = npz;
        sprinting = p.isSprinting();
        sneaking = p.isShiftKeyDown();
        now = level.getGameTime();
        tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        rain = level.getRainLevel(1F);
        thunder = level.getThunderLevel(1F);
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 cp = cam.position();
        camX = cp.x;
        camY = cp.y;
        camZ = cp.z;
        Vector3fc f = cam.forwardVector();
        fwdX = f.x();
        fwdY = f.y();
        fwdZ = f.z();
        Sites.tick(level, px, pz, now);
        WildSound.tick(level);
        Insects.tick(level);
        if (++clock % 10 == 0) populate(level);
        if (AUTOPILOT && clock % 600 == 0) report(level);
    }

    // ---- who lives here ----------------------------------------------------------------------------------------------

    private static void populate(ClientLevel level) {
        CENSUS.reset();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            WildCritter c = LIVE.get(i);
            if (c.isRemoved()) {
                LIVE.remove(i);
                continue;
            }
            double d = Math.hypot(c.getX() - px, c.getZ() - pz);
            boolean seen = inView(c.getX(), c.getY() + 0.2, c.getZ(), 0.45F);
            boolean gone = c.leaving && (!seen || d > 48) || d > WildKinds.REACH[c.kind] + WildKinds.SLACK && (!seen || d > 72)
                    || d > WildKinds.REACH[c.kind] + 60;
            if (c.state == SHOW) continue;
            if (gone || Sites.get(c.home) == null) {
                c.discard();
                LIVE.remove(i);
                continue;
            }
            if (!c.leaving) CENSUS.add(c.kind);
        }
        // nearest chunks first, so the caps keep what's close
        int n = 0;
        for (Sites.Site s : Sites.all()) {
            double d = Math.hypot(s.centreX() - px, s.centreZ() - pz);
            if (d > WildKinds.REACH[WildKinds.RAPTOR] + 8) continue;
            if (n == order.length) {
                order = Arrays.copyOf(order, n * 2);
                orderKey = Arrays.copyOf(orderKey, n * 2);
            }
            order[n] = s;
            orderKey[n] = ((long) (d * 16) << 20) | n;
            n++;
        }
        Arrays.sort(orderKey, 0, n);
        for (int i = 0; i < n; i++) {
            Sites.Site s = order[(int) (orderKey[i] & 0xFFFFF)];
            double d = orderKey[i] >>> 20;
            d /= 16;
            boolean city = CityLife.inside(s.centreX(), s.centreZ(), 24);
            for (int k = 0; k < WildKinds.BODIES; k++) {
                if (d > WildKinds.REACH[k] && !city) continue;
                int have = 0;
                WildCritter far = null;
                double farD = -1;
                for (WildCritter c : LIVE) {
                    if (c.home != s.key || c.kind != k || c.leaving) continue;
                    have++;
                    double cd = c.distanceToSqr(px, py, pz);
                    if (cd > farD) {
                        farD = cd;
                        far = c;
                    }
                }
                int step = CENSUS.decide(k, d, city, s.h, s.key, have, tod, rain, thunder, dial);
                if (step < 0 && far != null) {
                    far.leaving = true;
                    CENSUS.remove(k);
                } else if (step > 0 && spawn(level, k, s, have)) CENSUS.add(k);
            }
        }
        Insects.populate(level, order, orderKey, n, CENSUS);
    }

    /** Makes the slot-th animal of a kind on a chunk at its own spot; false when the spot isn't fit to show it now. */
    private static boolean spawn(ClientLevel level, int kind, Sites.Site s, int slot) {
        WildCritter c = new WildCritter(WildlifeModule.CRITTER.get(), level);
        c.kind = kind;
        c.home = s.key;
        c.seed = (int) (Density.draw(s.key, kind, slot + 11) * Integer.MAX_VALUE);
        c.variant = (int) (Density.draw(s.key, kind, slot + 23) * 64);
        RandomSource r = RandomSource.create(c.seed);
        if (!(WildKinds.flies(kind) ? Birds.place(c, level, s, slot, r) : Critters.place(c, level, s, slot, r))) return false;
        double x = c.getX(), y = c.getY(), z = c.getZ();
        if (!level.isLoaded(BlockPos.containing(x, y, z))) return false;
        // never pop up in plain sight: near and in view, wait (a bird in the sky further off, as it would be a speck)
        double d = Math.sqrt((x - camX) * (x - camX) + (y - camY) * (y - camY) + (z - camZ) * (z - camZ));
        if (d < (Birds.airborne(c) ? 48 : 26) && inView(x, y, z, 0.25F)) return false;
        if (Soundscape.hushAt(x, y, z) < 0.6F || WildSound.hostileNear(x, y, z, 12)) return false;
        c.setId(nextId--);
        c.xo = x;
        c.yo = y;
        c.zo = z;
        c.yRotO = c.getYRot();
        c.since = now;
        level.addEntity(c);
        LIVE.add(c);
        return true;
    }

    // ---- the studio (tours) ------------------------------------------------------------------------------------------

    /** A held pose for the line-up: the brain only keeps the clip. */
    static final int SHOW = 99;

    /**
     * One of each body in a row on the ground before the camera, 2.2 blocks apart, playing {@code clip} (or its idle),
     * facing the camera: the art's look and scale in the game's own light. Cleared by {@code lineup off}.
     */
    static String lineup(ClientLevel level, String clip, int variant) {
        for (WildCritter c : LIVE) if (c.state == SHOW) c.discard();
        LIVE.removeIf(WildCritter::isRemoved);
        if (clip.equals("off")) {
            Insects.clear();
            return "line-up cleared";
        }
        if (clip.equals("insects")) {
            float yaw = Minecraft.getInstance().player.getYRot() * Mth.DEG_TO_RAD;
            return Insects.demo(level, px, pz, yaw) + " insects in the line-up";
        }
        float yaw = Minecraft.getInstance().player.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -fz, rz = fx;
        int n = 0;
        for (int k = 0; k < WildKinds.BODIES; k++) {
            // two rows of six: the birds behind, the rest before them, side-on to the camera
            int row = k < 6 ? 1 : 0, col = k % 6;
            double off = (col - 2.5) * 1.7, d = row == 1 ? 6.0 : 3.8;
            double x = px + fx * d + rx * off, z = pz + fz * d + rz * off, y = ground(level, x, z);
            if (Double.isNaN(y)) continue;
            WildCritter c = new WildCritter(WildlifeModule.CRITTER.get(), level);
            c.kind = k;
            c.variant = variant;
            c.seed = 1000 + k;
            c.home = net.minecraft.world.level.ChunkPos.pack(Mth.floor(px) >> 4, Mth.floor(pz) >> 4);
            c.state = SHOW;
            c.setPos(x, y, z);
            c.setYRot((float) (Mth.atan2(pz - z, px - x) * Mth.RAD_TO_DEG) - 90F - 70F);
            c.yRotO = c.getYRot();
            c.xo = x;
            c.yo = y;
            c.zo = z;
            c.setId(nextId--);
            clip(c, CLIPS[k].containsKey(clip) ? clip : CLIPS[k].containsKey("idle") ? "idle" : "stand");
            level.addEntity(c);
            LIVE.add(c);
            n++;
        }
        return n + " in the line-up playing " + clip;
    }

    // ---- the brains' shared tools ----------------------------------------------------------------------------------

    private static void think(WildCritter c) {
        if (!c.level().isClientSide()) {
            c.discard();
            return;
        }
        c.rollO = c.roll;
        c.pitchO = c.pitch;
        c.speed = 0F;
        if (c.state == SHOW) return;
        try {
            if (WildKinds.flies(c.kind)) Birds.think(c, (ClientLevel) c.level());
            else Critters.think(c, (ClientLevel) c.level());
        } catch (RuntimeException ex) {
            Awaken.LOG.debug("[wildlife] a {} failed: {}", WildKinds.NAMES[c.kind], ex.toString());
            c.discard();
        }
    }

    static void clip(WildCritter c, String name) {
        Integer i = CLIPS[c.kind].get(name);
        int idx = i == null ? -1 : i;
        if (idx == c.clip) return;
        c.prevClip = c.clip;
        c.prevStart = c.clipStart;
        c.prevEnd = c.tickCount;
        c.clip = idx;
        c.clipStart = c.tickCount;
    }

    /** Restarts a one-shot clip even if it's the one playing (a second hop, another peck). */
    static void replay(WildCritter c, String name) {
        Integer i = CLIPS[c.kind].get(name);
        if (i == null) return;
        c.prevClip = c.clip;
        c.prevStart = c.clipStart;
        c.prevEnd = c.tickCount;
        c.clip = i;
        c.clipStart = c.tickCount;
    }

    static boolean playing(WildCritter c, String name) {
        Integer i = CLIPS[c.kind].get(name);
        return i != null && i == c.clip;
    }

    static double dist2(WildCritter c) {
        double dx = c.getX() - px, dy = c.getY() - py, dz = c.getZ() - pz;
        return dx * dx + dy * dy * 0.5 + dz * dz;
    }

    /** The player close, or moving fast within {@code run}; sneaking halves both. */
    static boolean threat(WildCritter c, double near, double run) {
        double d2 = dist2(c);
        double k = sneaking ? 0.5 : 1;
        return d2 < near * near * k * k || ((sprinting || pSpeed > 0.2) && d2 < run * run);
    }

    static void lookAtPlayer(WildCritter c, double within) {
        if (dist2(c) < within * within) {
            c.lookX = px;
            c.lookY = py + 1.5;
            c.lookZ = pz;
        } else c.lookX = Double.NaN;
    }

    /** Turns toward a heading at most {@code rate} degrees a tick; returns the turn made (for a bank). */
    static float face(WildCritter c, double dx, double dz, float rate) {
        if (dx * dx + dz * dz < 1e-8) return 0F;
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float d = Mth.clamp(Mth.wrapDegrees(want - c.getYRot()), -rate, rate);
        c.setYRot(c.getYRot() + d);
        return d;
    }

    /** Within the view cone (cos of the half angle) from the camera. */
    static boolean inView(double x, double y, double z, float cos) {
        double dx = x - camX, dy = y - camY, dz = z - camZ, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-3) return true;
        return (dx * fwdX + dy * fwdY + dz * fwdZ) / l > cos;
    }

    /** The first free y above the ground (no leaves) at a column, or NaN where the chunk isn't in. */
    static double ground(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** The first free y above everything (leaves and water included). */
    static double top(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    // ---- tours ------------------------------------------------------------------------------------------------------

    /** What's about, and what the camera can actually see (in the view cone, nothing solid between). */
    static String counts(ClientLevel level) {
        StringBuilder b = new StringBuilder();
        int[] near = new int[WildKinds.COUNT], seen = new int[WildKinds.COUNT];
        Vec3 eye = new Vec3(camX, camY, camZ);
        for (WildCritter c : LIVE) {
            if (c.isRemoved()) continue;
            near[c.kind]++;
            Vec3 at = new Vec3(c.getX(), c.getY() + 0.15, c.getZ());
            if (at.distanceTo(eye) < 64 && inView(at.x, at.y, at.z, 0.6F)
                    && level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext_empty()))
                    .getType() == HitResult.Type.MISS) seen[c.kind]++;
        }
        Insects.counts(near, seen);
        int ground = 0, low = 0, high = 0, air = 0;
        for (WildCritter c : LIVE) {
            if (c.isRemoved() || c.kind != WildKinds.SONGBIRD) continue;
            if (Birds.airborne(c)) air++;
            else if (c.state == Birds.GROUND || c.state == Birds.HOP) ground++;
            else if (c.getY() - ground(level, c.getX(), c.getZ()) <= 6) low++;
            else high++;
        }
        int total = 0, inView = 0;
        for (int k = 0; k < WildKinds.COUNT; k++) {
            if (near[k] == 0) continue;
            b.append(WildKinds.NAMES[k]).append(' ').append(seen[k]).append('/').append(near[k]).append(", ");
            total += near[k];
            inView += seen[k];
        }
        return "in view " + inView + " of " + total + " near: " + (b.isEmpty() ? "none" : b.substring(0, b.length() - 2))
                + " (" + Sites.size() + " chunks read, " + String.format("%02d:%02d", (tod / 1000 + 6) % 24, tod % 1000 * 60 / 1000)
                + (rain > 0.2F ? ", rain" : "") + "; songbirds ground " + ground + ", low " + low + ", high " + high + ", air " + air + ")";
    }

    private static net.minecraft.world.phys.shapes.CollisionContext CollisionContext_empty() {
        return net.minecraft.world.phys.shapes.CollisionContext.empty();
    }

    private static void report(ClientLevel level) {
        Awaken.LOG.info("[wildlife] {}", counts(level));
    }
}
