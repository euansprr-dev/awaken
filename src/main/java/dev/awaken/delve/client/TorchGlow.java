package dev.awaken.delve.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.delve.HeldLight;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Light carried in a hand (SPEC §28.3, "Held light"): a torch, a lantern, glowstone, any held block that gives light
 * ({@link HeldLight}) lights the world round whoever holds it, drawn and never placed (no light blocks, nothing relit).
 * <p>The light finds its surfaces the way the game's own light spreads: a flood from the flame through open cells (a
 * sphere of its reach, walls stop it, so it turns corners but never goes through rock), done a few hundred cells a frame
 * and redone when the flame moves to another block. Each face it reaches (and each plant, slab or lantern in a reached
 * cell) is drawn again over itself with its own quads, additively, in the light's colour, by how much brighter the
 * game's light curve would make it there than it already is: nothing at noon, the most in a cave. In the open it also
 * reads like the Ember: a soft pool at your feet and a halo at the hand (hidden in your own first person). Flames
 * flicker; glowstone and sea lanterns are steady. Every player within {@link #OTHERS} carries theirs.
 */
public final class TorchGlow {
    private TorchGlow() {}

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** Others' held lights are drawn within this of you; at most SLOTS lights at once (yours first). */
    static final double OTHERS = 48;
    static final int SLOTS = 12;
    /** The surfaces one light may draw (yours, another's), nearest first, and the quads all of them may draw a frame. */
    static final int MAX_OWN = 640, MAX_OTHER = 240, MAX_QUADS = 2600;
    /** Flood cells walked a frame (all lights, one at a time), and a still light's refresh (blocks change). */
    static final int CELLS_PER_FRAME = 900, REFRESH_TICKS = 10;
    /** A flood step's worth of distance: light that had to go round a wall is dimmer than its straight line says. */
    static final float STEP = 0.55F;
    /** The lit surfaces' gain over the game's own light curve, and how far in from the reach the light starts to fade. */
    static final float BRIGHT = 1.15F, FADE = 0.3F;
    /** The pool at the feet (its radius a share of the reach, its strength at a level 15 light in the dark) and the halo. */
    static final float POOL_R = 0.55F, POOL_A = 0.15F, HALO = 0.6F, HALO_A = 0.5F, CORE = 0.055F;

    /** Mods that already light the world round held items: with one in, only the halo and pool are drawn. */
    private static final String[] DYNAMIC_LIGHTS = {"lambdynlights", "sodiumdynamiclights", "ryoamiclights", "dynamiclights"};
    private static int othersLight = -1;

    private static boolean surfaces() {
        if (othersLight < 0) {
            othersLight = 0;
            for (String id : DYNAMIC_LIGHTS) if (ModList.get().isLoaded(id)) othersLight = 1;
        }
        return othersLight == 0;
    }

    // ---- the lights ----------------------------------------------------------------------------------------------------

    /** One carried light: whose, its kind, its flame this frame and the surfaces its last flood reached. */
    private static final class Light {
        int id = -1;
        boolean seen, own, fixed;
        HeldLight.Kind kind = HeldLight.NONE;
        double x, y, z, feet;
        float flicker, dark;
        boolean dim;            // as bright where it stands as its own light: nothing drawn but the halo
        long origin = Long.MIN_VALUE, builtTick;
        HeldLight.Kind builtKind = HeldLight.NONE;
        int n;
        long[] pos = new long[MAX_OWN];
        byte[] face = new byte[MAX_OWN], steps = new byte[MAX_OWN], lit = new byte[MAX_OWN];
    }

    private static final Light[] LIGHTS = new Light[SLOTS];

    /**
     * The Kindle embers burning in this level (SPEC §34.4, set by {@link PickFx}): each one's air cell and the face of
     * the rock it's stuck on. Each within {@link #OTHERS} is a light of its own, a slot after the players'.
     */
    static long[] emberAt = new long[0];
    static byte[] emberFace = new byte[0];
    private static final HeldLight.Kind EMBER = new HeldLight.Kind(12, 0xFF9A50, true);
    private static final int EMBER_ID = 1_900_000_000;

    static {
        for (int i = 0; i < SLOTS; i++) LIGHTS[i] = new Light();
    }

    // the flood: one light at a time, into its own back buffer, swapped in when done
    private static Light building;
    private static int buildCap;
    private static float buildReach;
    private static long buildFrom;
    private static final long[] Q = new long[8192];
    private static final byte[] QS = new byte[8192];
    private static int head, tail, bn;
    private static long[] bPos = new long[MAX_OWN];
    private static byte[] bFace = new byte[MAX_OWN], bSteps = new byte[MAX_OWN], bLit = new byte[MAX_OWN];
    private static final LongOpenHashSet SEEN = new LongOpenHashSet(8192);
    private static final Direction[] DIRS = Direction.values();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos(), N = new BlockPos.MutableBlockPos();

    // ---- per frame -----------------------------------------------------------------------------------------------------

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static double cx, cy, cz;
    private static float pt;
    private static int darken;
    private static ClientLevel level;

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        level = mc.level;
        if (level == null || mc.player == null || !DelveClientConfig.heldLight()) {
            if (building != null) building = null;
            return;
        }
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        darken = level.getSkyDarken();
        RenderType lit = surfaces() ? DelveRender.lit() : null;
        try {
            if (!gather(mc)) return;
            if (lit != null) flood();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] held light failed", ex);
            building = null;
            return;
        }
        if (lit != null) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), lit, DRAW_LIT);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), DelveRender.glow(), DRAW_GLOW);
    }

    /** Finds this frame's lights (a slot per player who holds one, yours first); false when there are none. */
    private static boolean gather(Minecraft mc) {
        for (Light l : LIGHTS) l.seen = false;
        List<AbstractClientPlayer> players = level.players();
        boolean any = false;
        float t = (level.getGameTime() % 24000L + pt) / 20F;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < players.size(); i++) {
                AbstractClientPlayer p = players.get(i);
                boolean own = p == mc.player;
                if (own != (pass == 0) || p.isSpectator() || p.isRemoved()) continue;
                boolean off = HeldLight.offhandWins(p.getMainHandItem(), p.getOffhandItem());
                HeldLight.Kind k = HeldLight.of(off ? p.getOffhandItem() : p.getMainHandItem());
                if (k.emission() <= 0 || !own && p.distanceToSqr(mc.player) > OTHERS * OTHERS) continue;
                Light l = slot(p.getId());
                if (l == null) continue;
                l.seen = true;
                l.own = own;
                l.fixed = false;
                l.kind = k;
                flame(mc, l, p, off);
                l.flicker = k.flame() ? 0.93F + 0.045F * Mth.sin(t * 9.1F + p.getId() * 0.37F) + 0.025F * Mth.sin(t * 23.7F + p.getId() * 0.85F) : 1F;
                M.set(Mth.floor(l.x), Mth.floor(l.y), Mth.floor(l.z));
                int here = existing(level.getBrightness(LightLayer.BLOCK, M), level.getBrightness(LightLayer.SKY, M));
                l.dark = 1F - curve(here);
                l.dim = here >= k.emission();
                any = true;
            }
        }
        long[] at = emberAt;
        byte[] face = emberFace;
        for (int i = 0; i < at.length && i < face.length; i++) {
            Direction d = Direction.from3DDataValue(face[i]);
            double x = BlockPos.getX(at[i]) + 0.5 + d.getStepX() * 0.38, y = BlockPos.getY(at[i]) + 0.5 + d.getStepY() * 0.38, z = BlockPos.getZ(at[i]) + 0.5 + d.getStepZ() * 0.38;
            if (mc.player.distanceToSqr(x, y, z) > OTHERS * OTHERS) continue;
            Light l = slot(EMBER_ID + i);
            if (l == null) continue;
            l.seen = true;
            l.own = false;
            l.fixed = true;
            l.kind = EMBER;
            l.x = x;
            l.y = y;
            l.z = z;
            l.feet = BlockPos.getY(at[i]);
            l.flicker = 0.9F + 0.06F * Mth.sin(t * 7.3F + i * 1.7F) + 0.04F * Mth.sin(t * 19.1F + i * 0.6F);
            M.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            int here = existing(level.getBrightness(LightLayer.BLOCK, M), level.getBrightness(LightLayer.SKY, M));
            l.dark = 1F - curve(here);
            l.dim = here >= EMBER.emission();
            any = true;
        }
        for (Light l : LIGHTS) {
            if (l.seen || l.id < 0) continue;
            if (building == l) building = null;
            l.id = -1;
            l.n = 0;
            l.origin = Long.MIN_VALUE;
        }
        return any;
    }

    private static Light slot(int id) {
        Light free = null;
        for (Light l : LIGHTS) {
            if (l.id == id) return l;
            if (free == null && l.id < 0) free = l;
        }
        if (free != null) {
            free.id = id;
            free.n = 0;
            free.origin = Long.MIN_VALUE;
        }
        return free;
    }

    /**
     * Where the flame is: in your own first person a little below and ahead of the eye on the holding hand's side (it
     * follows your look); otherwise in the hand, held at the side, as the Ember is.
     */
    private static void flame(Minecraft mc, Light l, AbstractClientPlayer p, boolean off) {
        double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
        double side = (!off == (p.getMainArm() == HumanoidArm.RIGHT)) ? -1 : 1;
        l.feet = y;
        if (l.own && p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            float yaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
            l.x = x + fx * 0.4 + lx * side * 0.3;
            l.y = y + p.getEyeHeight() - 0.32;
            l.z = z + fz * 0.4 + lz * side * 0.3;
        } else {
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
            l.x = x + fx * 0.3 + lx * side * 0.38;
            l.y = y + (p.isCrouching() ? 0.78 : 0.98);
            l.z = z + fz * 0.3 + lz * side * 0.38;
        }
    }

    // ---- the flood -----------------------------------------------------------------------------------------------------

    /** Walks this frame's share of the flood, starting the next light that needs one when none is under way. */
    private static void flood() {
        if (building == null) start();
        int budget = CELLS_PER_FRAME;
        while (building != null && budget > 0) {
            budget -= step(budget);
            if (head >= tail || bn >= buildCap) finish();
            if (building == null && budget > 0) start();
        }
    }

    /** Picks the light whose surfaces are stale (moved a block, its kind changed, or REFRESH_TICKS old), yours first. */
    private static void start() {
        long now = level.getGameTime();
        Light best = null;
        for (Light l : LIGHTS) {
            if (l.id < 0 || !l.seen || l.dim) continue;
            long o = BlockPos.asLong(Mth.floor(l.x), Mth.floor(l.y), Mth.floor(l.z));
            boolean stale = o != l.origin || l.kind != l.builtKind || now - l.builtTick >= REFRESH_TICKS || now < l.builtTick;
            if (!stale) continue;
            if (best == null || l.own) best = l;
            if (l.own) break;
        }
        if (best == null) return;
        Light l = best;
        int ox = Mth.floor(l.x), oy = Mth.floor(l.y), oz = Mth.floor(l.z);
        M.set(ox, oy, oz);
        if (level.getBlockState(M).isSolidRender()) {
            // the hand is in a wall: start from the cell above the feet
            oy = Mth.floor(l.feet + 0.5);
            M.set(ox, oy, oz);
            if (level.getBlockState(M).isSolidRender()) return;
        }
        building = l;
        buildFrom = BlockPos.asLong(Mth.floor(l.x), Mth.floor(l.y), Mth.floor(l.z));
        buildCap = l.own ? MAX_OWN : MAX_OTHER;
        buildReach = l.kind.reach();
        l.builtKind = l.kind;
        SEEN.clear();
        long o = BlockPos.asLong(ox, oy, oz);
        SEEN.add(o);
        Q[0] = o;
        QS[0] = 0;
        head = 0;
        tail = 1;
        bn = 0;
    }

    /** Walks up to {@code budget} cells; returns how many it walked. */
    private static int step(int budget) {
        long o = Q[0];
        int ox = BlockPos.getX(o), oy = BlockPos.getY(o), oz = BlockPos.getZ(o);
        float r2 = (buildReach + 0.5F) * (buildReach + 0.5F);
        int maxSteps = Math.min(120, (int) (buildReach / STEP) + 2);
        int walked = 0;
        while (head < tail && walked < budget && bn < buildCap) {
            long c = Q[head];
            int s = QS[head];
            head++;
            walked++;
            for (Direction d : DIRS) {
                long nb = BlockPos.offset(c, d);
                int x = BlockPos.getX(nb), y = BlockPos.getY(nb), z = BlockPos.getZ(nb);
                int dx = x - ox, dy = y - oy, dz = z - oz;
                if (dx * dx + dy * dy + dz * dz > r2 || level.isOutsideBuildHeight(y) || !SEEN.add(nb)) continue;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                M.set(x, y, z);
                BlockState st = level.getBlockState(M);
                if (st.isSolidRender()) {
                    // a wall: its face toward the cell the light came from
                    N.set(BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c));
                    record(nb, d.getOpposite().ordinal(), s + 1, N);
                    if (bn >= buildCap) break;
                    continue;
                }
                int ns = s + 1 + st.getLightDampening();
                if (!st.isAir() && st.getFluidState().isEmpty()) {
                    record(nb, 6, ns, M);       // a plant, a slab, a lantern: the whole of it
                    if (bn >= buildCap) break;
                }
                if (ns < maxSteps && tail < Q.length) {
                    Q[tail] = nb;
                    QS[tail] = (byte) ns;
                    tail++;
                }
            }
        }
        return Math.max(1, walked);
    }

    /** One surface reached: where, which face (6: the whole block), how far by the flood, and the light already there. */
    private static void record(long p, int face, int steps, BlockPos lightAt) {
        bPos[bn] = p;
        bFace[bn] = (byte) face;
        bSteps[bn] = (byte) Math.min(127, steps);
        int b = level.getBrightness(LightLayer.BLOCK, lightAt), s = level.getBrightness(LightLayer.SKY, lightAt);
        bLit[bn] = (byte) (b << 4 | s);
        bn++;
    }

    /** The flood is done: its surfaces become the light's (the buffers swap, nothing is copied or allocated). */
    private static void finish() {
        Light l = building;
        building = null;
        long[] p = l.pos;
        byte[] f = l.face, s = l.steps, lt = l.lit;
        l.pos = bPos;
        l.face = bFace;
        l.steps = bSteps;
        l.lit = bLit;
        l.n = bn;
        bPos = p;
        bFace = f;
        bSteps = s;
        bLit = lt;
        l.origin = buildFrom;
        l.builtTick = level.getGameTime();
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    // the light and surface being drawn
    private static double lx, ly, lz;
    private static float emission, reach, flick, floorDist, hereCurve;
    private static int rgb;

    /** The game's light curve (a level's brightness on the lightmap, before gamma). */
    static float curve(int lightLevel) {
        return curve(lightLevel / 15F);
    }

    private static float curve(float v) {
        v = Mth.clamp(v, 0F, 1F);
        return v / (4F - 3F * v);
    }

    private static int existing(int block, int sky) {
        return Math.max(block, sky - darken);
    }

    /** The light's colour at a vertex: what the flame adds over the light already there, turned to its face. */
    private static final DelveRender.QuadLight SURFACE = (x, y, z, n) -> {
        double dx = lx - x, dy = ly - y, dz = lz - z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= reach) return 0;
        float de = Math.max((float) d, floorDist);
        float add = curve((emission - de) / 15F) - hereCurve;
        if (add <= 0) return 0;
        float fade = Mth.clamp((reach - (float) d) / (reach * FADE), 0F, 1F);
        fade = fade * fade * (3 - 2 * fade);
        float turn = 0.8F;
        if (n != null && d > 1e-3) turn = 0.4F + 0.6F * Math.max(0F, (float) ((dx * n.getStepX() + dy * n.getStepY() + dz * n.getStepZ()) / d));
        return DelveRender.col(rgb, add * BRIGHT * turn * fade * flick);
    };

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_LIT = (pose, vc) -> {
        try {
            if (level == null) return;
            int quads = 0;
            for (int pass = 0; pass < 2 && quads < MAX_QUADS; pass++) {
                for (Light l : LIGHTS) {
                    if (l.id < 0 || !l.seen || l.dim || l.n == 0 || l.own != (pass == 0)) continue;
                    quads += drawSurfaces(vc, l, MAX_QUADS - quads);
                    if (quads >= MAX_QUADS) break;
                }
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] held light failed", ex);
            DelveRender.litOk = 0;
        }
    };

    /** One light's surfaces, nearest first, up to {@code cap} quads. */
    private static int drawSurfaces(VertexConsumer vc, Light l, int cap) {
        lx = l.x;
        ly = l.y;
        lz = l.z;
        emission = l.kind.emission();
        reach = l.kind.reach();
        flick = l.flicker;
        rgb = l.kind.rgb();
        float near = reach + 0.9F;
        int drawn = 0;
        for (int i = 0; i < l.n && drawn < cap; i++) {
            long p = l.pos[i];
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            double dx = x + 0.5 - lx, dy = y + 0.5 - ly, dz = z + 0.5 - lz;
            float dc = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dc > near) continue;
            int lit = l.lit[i] & 255;
            int here = existing(lit >> 4, lit & 15);
            if (emission - Math.max(0F, dc - 0.9F) <= here) continue;   // already as bright as the flame could make it
            hereCurve = curve(here);
            floorDist = Math.max(0F, l.steps[i] * STEP - 1.2F);
            M.set(x, y, z);
            BlockState s = level.getBlockState(M);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            int f = l.face[i];
            if (f < 6) {
                N.setWithOffset(M, DIRS[f]);
                if (level.getBlockState(N).isSolidRender()) continue;   // built over since the flood
            }
            drawn += DelveRender.litBlock(vc, level, s, M, f, SURFACE, cx, cy, cz);
        }
        return drawn;
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_GLOW = (pose, vc) -> {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (level == null) return;
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            for (Light l : LIGHTS) {
                if (l.id < 0 || !l.seen) continue;
                float lvl = l.kind.emission() / 15F;
                // the pool on the ground under the flame: only where it is dark enough to see it
                float pool = l.fixed ? 0F : POOL_A * lvl * l.dark * l.flicker;
                DelveRender.softPool(vc, (float) (l.x - cx), (float) (l.feet + 0.03 - cy), (float) (l.z - cz), l.kind.reach() * POOL_R, l.kind.rgb(), pool);
                if (l.own && firstPerson) continue;
                float x = (float) (l.x - cx), y = (float) (l.y - cy), z = (float) (l.z - cz);
                float a = HALO_A * (0.35F + 0.65F * l.dark) * l.flicker, r = HALO * Mth.sqrt(lvl) * l.flicker;
                DelveRender.disc(vc, x, y, z, r, DelveRender.col(l.kind.rgb(), a), DelveRender.col(l.kind.rgb(), 0F), RIGHT, UP);
                DelveRender.disc(vc, x, y, z, r * 0.42F, DelveRender.col(l.kind.rgb(), a * 1.3F), DelveRender.col(l.kind.rgb(), 0F), RIGHT, UP);
                DelveRender.disc(vc, x, y, z, CORE, DelveRender.col(whiter(l.kind.rgb()), 0.9F * l.flicker), DelveRender.col(l.kind.rgb(), 0F), RIGHT, UP);
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] held light glow failed", ex);
        }
    };

    /** The flame's hot core: its colour most of the way to white. */
    private static int whiter(int c) {
        int r = (c >> 16 & 255) + 3 * 255 >> 2, g = (c >> 8 & 255) + 3 * 255 >> 2, b = (c & 255) + 3 * 255 >> 2;
        return r << 16 | g << 8 | b;
    }

    static void reset() {
        building = null;
        for (Light l : LIGHTS) {
            l.id = -1;
            l.n = 0;
            l.origin = Long.MIN_VALUE;
        }
    }
}
