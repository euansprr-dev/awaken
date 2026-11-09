package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.WaveSurface;
import dev.awaken.powers.fx.FxBudget;
import dev.awaken.powers.fx.FxPool;
import dev.awaken.powers.fx.FxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static dev.awaken.powers.client.fx.FxWorld.R;
import static dev.awaken.powers.client.fx.FxWorld.alpha;
import static dev.awaken.powers.fx.PowerFxParticles.CRACK;
import static dev.awaken.powers.fx.PowerFxParticles.DUST;

/**
 * Shockwave's wave in the ground itself (SPEC §50.4): the real blocks along its path rise and settle as it passes, with their own
 * textures and tints, whatever sits on them riding along. It walks the same ground as the server ({@link WaveSurface}), one tick's
 * worth of front at a time, so it climbs what the hits climb and stops where they stop. Nothing changes in the world. Fixed pools,
 * no allocation once a block's quads are known; the column count is the debris budget, so Minimal draws none.
 */
final class FxGroundWave {
    private FxGroundWave() {}

    private static final int CAP = FxBudget.debrisCap(FxBudget.Level.FULL);
    private static final int[] BX = new int[CAP], BY = new int[CAP], BZ = new int[CAP], START = new int[CAP], LIGHT = new int[CAP], TINT = new int[CAP];
    private static final float[] AMP = new float[CAP];
    private static final BakedQuad[][] QUADS = new BakedQuad[CAP][];
    private static int live;

    /** Ticks a column takes to rise, drop and settle. */
    private static final int COLUMN_LIFE = 11;
    /** Blocks from the foot before the ground starts moving: the feet stay planted. */
    private static final float PLANTED = 1.2f;
    private static final int MAX_WAVES = 4, MAX_RAYS = 64, SEEN = 512;
    private static final int WHITE = 0xFFFFFFFF;

    private static final class Wave {
        boolean live;
        double x, z;
        int age, ticks, rays, level;
        float range, dirX, dirZ;
        boolean walled;
        FxStyle st = FxStyle.KINETIC;
        final float[] angle = new float[MAX_RAYS], dist = new float[MAX_RAYS];
        final int[] y = new int[MAX_RAYS];
        final boolean[] alive = new boolean[MAX_RAYS];
        final long[] seen = new long[SEEN];

        /** True the first time a column is met; every ray crossing it after that leaves it alone. */
        boolean first(int bx, int bz) {
            long key = ((long) bx << 32 ^ bz & 0xFFFFFFFFL) | 1L << 62; // never 0, which marks an empty slot
            int i = Long.hashCode(key * 0x9E3779B97F4A7C15L) & SEEN - 1;
            for (int probes = 0; probes < SEEN; probes++, i = i + 1 & SEEN - 1) {
                if (seen[i] == key) return false;
                if (seen[i] == 0) {
                    seen[i] = key;
                    return true;
                }
            }
            return false;
        }
    }

    private static final Wave[] WAVES = new Wave[MAX_WAVES];
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();
    private static final Map<BlockState, BakedQuad[]> CACHE = new IdentityHashMap<>();
    private static final BakedQuad[] NONE = new BakedQuad[0];
    private static final Direction[] DRAWN = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final RandomSource MODEL_RANDOM = RandomSource.create();
    private static RenderType type;
    private static final SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, vc) -> {
        try {
            draw(vc);
        } catch (RuntimeException ex) {
            dev.awaken.Awaken.LOG.warn("Power FX ground wave failed to draw; clearing it", ex);
            clear();
        }
    };
    private static double cx, cy, cz;
    private static float pt;

    static {
        for (int i = 0; i < MAX_WAVES; i++) WAVES[i] = new Wave();
    }

    static boolean any() {
        return live > 0;
    }

    static void clear() {
        live = 0;
        java.util.Arrays.fill(QUADS, null);
        for (Wave w : WAVES) w.live = false;
        CACHE.clear(); // a resource reload bakes new models
    }

    /**
     * Sends a wave out from the ground under (x, y, z). False when nothing will ripple: no ground in reach, or a setting that
     * allows no debris; the caller then draws the plain arc.
     */
    static boolean start(double x, double y, double z, float dx, float dz, float range, float halfAngle, int ticks, FxStyle st, int level) {
        ClientLevel lvl = Minecraft.getInstance().level;
        if (lvl == null || FxBudget.debrisCap(FxWorld.level) == 0) return false;
        int ground = WaveSurface.start(lvl, x, y, z);
        if (ground == WaveSurface.NONE) return false;
        Wave w = null;
        for (Wave o : WAVES) {
            if (!o.live) {
                w = o;
                break;
            }
        }
        if (w == null) return false;
        w.live = true;
        w.x = x;
        w.z = z;
        w.age = 0;
        w.ticks = Math.max(1, ticks);
        w.range = range;
        w.dirX = dx;
        w.dirZ = dz;
        w.level = level;
        w.st = st;
        w.walled = false;
        java.util.Arrays.fill(w.seen, 0);
        w.rays = Mth.clamp(Mth.ceil(2 * halfAngle * range / 0.6f) + 1, 3, MAX_RAYS);
        float base = (float) Math.atan2(dz, dx);
        for (int i = 0; i < w.rays; i++) {
            w.angle[i] = base + halfAngle * (2f * i / (w.rays - 1) - 1f);
            w.dist[i] = 0;
            w.y[i] = ground;
            w.alive[i] = true;
        }
        return true;
    }

    static void tick(ClientLevel level) {
        for (Wave w : WAVES) if (w.live) advance(w, level);
        int i = 0;
        while (i < live) {
            if (FxWorld.ticks - START[i] >= COLUMN_LIFE) {
                int l = --live;
                BX[i] = BX[l];
                BY[i] = BY[l];
                BZ[i] = BZ[l];
                START[i] = START[l];
                LIGHT[i] = LIGHT[l];
                TINT[i] = TINT[l];
                AMP[i] = AMP[l];
                QUADS[i] = QUADS[l];
                QUADS[l] = null;
                continue;
            }
            i++;
        }
    }

    private static void advance(Wave w, ClientLevel level) {
        float front = w.range * (w.age + 1) / w.ticks;
        for (int r = 0; r < w.rays; r++) {
            float ca = Mth.cos(w.angle[r]), sa = Mth.sin(w.angle[r]);
            while (w.alive[r] && w.dist[r] + WaveSurface.STEP <= front) {
                float d = w.dist[r] += (float) WaveSurface.STEP;
                double px = w.x + ca * d, pz = w.z + sa * d;
                int bx = Mth.floor(px), bz = Mth.floor(pz);
                int top = WaveSurface.top(level, MUT, bx, w.y[r], bz, WaveSurface.CLIMB, WaveSurface.DROP);
                if (top == WaveSurface.NONE) {
                    w.alive[r] = false;
                    stopped(w, level, r, px, w.y[r], pz, ca, sa);
                    break;
                }
                w.y[r] = top;
                if (d >= PLANTED && w.first(bx, bz)) column(w, level, bx, top, bz, d / w.range, ca, sa);
            }
        }
        if (++w.age >= w.ticks) {
            w.live = false;
            double tx = w.x + w.dirX * w.range, tz = w.z + w.dirZ * w.range;
            int mid = w.rays / 2;
            if (w.alive[mid]) {
                level.playLocalSound(tx, w.y[mid] + 1, tz, (w.level >= 5 ? PowerSounds.WAVE_L5 : PowerSounds.WAVE_TAIL).value(), SoundSource.PLAYERS, 1.2f, 1f, false);
            }
        }
    }

    /** The front has run into something it can't climb: it breaks against it, and the middle of it is heard doing so. */
    private static void stopped(Wave w, ClientLevel level, int ray, double x, int y, double z, float ca, float sa) {
        int p = FxWorld.sprite(DUST, x, y + 1.3, z, -ca * 0.03, 0.05, -sa * 0.03, 26 + R.i(10), 1f, R.f(2f, 3f), dustColour(w.st), alpha(w.st.accent, 0), FxPool.LIT | FxPool.ANIM);
        FxWorld.drag(p, 0.9f);
        if (w.walled || Math.abs(ray - w.rays / 2) > w.rays / 4) return;
        w.walled = true;
        level.playLocalSound(x, y + 1, z, PowerSounds.WAVE_WALL.value(), SoundSource.PLAYERS, 1.1f, 1f, false);
    }

    private static void column(Wave w, ClientLevel level, int x, int y, int z, float t, float ca, float sa) {
        MUT.set(x, y, z);
        BlockState s = level.getBlockState(MUT);
        double mx = x + 0.5, mz = z + 0.5, top = y + 1;
        if (!s.getFluidState().isEmpty() && s.getCollisionShape(level, MUT).isEmpty()) {
            // water and lava throw up a spray instead of a block
            for (int i = FxWorld.n(3); i > 0; i--) {
                int p = FxWorld.sprite(DUST, mx + R.s() * 0.5, top, mz + R.s() * 0.5, ca * 0.06, R.f(0.1f, 0.22f), sa * 0.06, 14 + R.i(8), 0.5f, 1.5f,
                        alpha(0xFFCFE4F2, 0.7f), alpha(0xFFCFE4F2, 0), FxPool.LIT | FxPool.ANIM);
                FxWorld.drag(p, 0.92f);
            }
            return;
        }
        // the crest at the far end stands tallest
        float amp = 0.24f + 0.62f * t * t;
        add(level, s, x, y, z, amp);
        BlockState up = level.getBlockState(MUT.set(x, y + 1, z));
        if (!up.isAir() && up.getFluidState().isEmpty()) add(level, up, x, y + 1, z, amp); // grass, flowers and snow ride on it
        if (R.chance(0.45f)) {
            int p = FxWorld.sprite(DUST, mx, top + R.f(0.05f, 0.6f), mz, ca * 0.05, R.f(0.02f, 0.06f), sa * 0.05, 24 + R.i(14), 0.8f, R.f(1.6f, 2.6f) * (0.6f + t),
                    dustColour(w.st), alpha(w.st.accent, 0), FxPool.LIT | FxPool.ANIM);
            FxWorld.drag(p, 0.9f);
            FxWorld.floor(p, (float) top);
            FxWorld.spin(p, R.f(0, 6.28f), R.s() * 0.03f);
        }
        if (R.chance(0.12f + 0.3f * t)) {
            FxDebris.spawn(s, mx + R.s() * 0.3, top + 0.1, mz + R.s() * 0.3, ca * (0.05f + 0.1f * t), 0.22f + 0.35f * t, sa * (0.05f + 0.1f * t), R.f(0.12f, 0.24f),
                    24 + R.i(8), (float) top);
        }
        if (R.chance(0.2f)) FxRecipes.decal(mx, top, mz, CRACK, R.f(1.1f, 1.8f), 0xB82A2420, 80);
    }

    private static int dustColour(FxStyle st) {
        return alpha(FxWorld.mix(st.accent, 0xFFCFC6B5, 0.35f), 0.9f);
    }

    private static void add(ClientLevel level, BlockState s, int x, int y, int z, float amp) {
        if (live >= FxBudget.debrisCap(FxWorld.level)) return;
        BakedQuad[] q = quads(s);
        if (q.length == 0) return;
        int i = live++;
        BX[i] = x;
        BY[i] = y;
        BZ[i] = z;
        AMP[i] = amp;
        QUADS[i] = q;
        START[i] = FxWorld.ticks;
        LIGHT[i] = FxWorld.light(x + 0.5, y + 1.2, z + 0.5);
        BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(s, 0);
        TINT[i] = tint == null ? WHITE : 0xFF000000 | tint.colorInWorld(s, level, MUT.set(x, y, z));
    }

    /** The real model's faces, all but the underside. Glass-like (translucent) faces are left out: they'd draw as solid here. */
    private static BakedQuad[] quads(BlockState s) {
        BakedQuad[] have = CACHE.get(s);
        if (have != null) return have;
        List<BlockStateModelPart> parts = new ArrayList<>();
        List<BakedQuad> out = new ArrayList<>();
        try {
            MODEL_RANDOM.setSeed(42L);
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(s).collectParts(MODEL_RANDOM, parts);
            for (BlockStateModelPart part : parts) {
                for (Direction d : DRAWN) collect(part.getQuads(d), out);
                collect(part.getQuads(null), out);
            }
        } catch (RuntimeException e) {
            out.clear();
        }
        BakedQuad[] q = out.isEmpty() ? NONE : out.toArray(BakedQuad[]::new);
        CACHE.put(s, q);
        return q;
    }

    private static void collect(List<BakedQuad> in, List<BakedQuad> out) {
        for (BakedQuad q : in) if (q.materialInfo().layer() != ChunkSectionLayer.TRANSLUCENT) out.add(q);
    }

    static void submit(PoseStack ps, SubmitNodeCollector out, double camX, double camY, double camZ, float partial) {
        if (live == 0) return;
        cx = camX;
        cy = camY;
        cz = camZ;
        pt = partial;
        if (type == null) type = RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        out.submitCustomGeometry(ps, type, DRAW);
    }

    /** Height over the column's life, 0..1: a quick rise and drop, then one small bounce. */
    private static float height(float age) {
        float t = age / COLUMN_LIFE;
        if (t <= 0 || t >= 1) return 0;
        return t < 0.5f ? Mth.sin(Mth.PI * t / 0.5f) : 0.22f * Mth.sin(Mth.PI * (t - 0.5f) / 0.5f);
    }

    /** A hair larger than the block it copies, so its faces win over the real ones they sit on. */
    private static final float GROW = 1.003f;

    private static void draw(VertexConsumer vc) {
        for (int i = 0; i < live; i++) {
            float h = AMP[i] * height(FxWorld.ticks - START[i] + pt);
            if (h < 0.015f) continue; // the copy would fight the real block for the same pixels
            float ox = (float) (BX[i] - cx), oy = (float) (BY[i] + h - cy), oz = (float) (BZ[i] - cz);
            for (BakedQuad q : QUADS[i]) {
                int colour = q.materialInfo().tintIndex() >= 0 ? TINT[i] : WHITE;
                Vector3fc n = q.direction().getUnitVec3f();
                for (int v = 0; v < 4; v++) {
                    Vector3fc p = q.position(v);
                    long uv = q.packedUV(v);
                    vc.addVertex(ox + 0.5f + (p.x() - 0.5f) * GROW, oy + 0.5f + (p.y() - 0.5f) * GROW, oz + 0.5f + (p.z() - 0.5f) * GROW, colour,
                            UVPair.unpackU(uv), UVPair.unpackV(uv), OverlayTexture.NO_OVERLAY, LIGHT[i], n.x(), n.y(), n.z());
                }
            }
        }
    }
}
