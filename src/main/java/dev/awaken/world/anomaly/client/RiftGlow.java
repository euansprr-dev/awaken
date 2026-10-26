package dev.awaken.world.anomaly.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.world.anomaly.RiftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A rift tear's light, drawn (SPEC §26.4; the CityGlow way: one additive pass, depth-tested, no depth written, so the
 * near-white core feeds Lens's bloom and nothing is relit). A jagged crack seven blocks tall whose edges crawl, three
 * ribbons deep (a violet glow, a hot magenta body, a white core) with a few side cracks that flicker; a wide halo; a
 * pool of violet on the ground; a faint column into the sky so it reads from 60 blocks by day; and motes drawn in
 * spirals into the crack. It opens with a flash and a ring along the ground, flares when its core is struck, and
 * folds in on itself as it closes. While a rift is near, the world's birds and insects hush.
 */
public final class RiftGlow {
    private RiftGlow() {}

    // ---- the pipeline (additive, depth-tested, no depth written) -----------------------------------------------------

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline LIGHT = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/rift_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final OitPipelineSet LIGHT_OIT = OitPipelineSet.builder(Awaken.id("rift_glow"),
            RenderPipeline.builder(SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();
    private static RenderType type;
    private static int ok = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(LIGHT);
        e.registerOitPipelineSet(LIGHT_OIT);
    }

    private static RenderType light() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(LIGHT) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
        }
        if (ok == 0) return RenderTypes.lightning();
        if (type == null) type = RenderType.create("awaken_rift_glow", RenderSetup.builder(LIGHT).setOitPipelines(LIGHT_OIT).createRenderSetup());
        return type;
    }

    // ---- the numbers ------------------------------------------------------------------------------------------------

    /** The crack: its foot above the core, its height and widest half-width (blocks). */
    static final float FOOT = 0.7F, HEIGHT = 7.0F, HALF_W = 0.55F;
    /** Halo radius, ground pool radius and strength, sky column height. */
    static final float HALO = 6.5F, POOL = 11.0F, POOL_A = 0.42F, COLUMN = 56.0F;
    static final int NODES = 16, MOTES = 28, RING = 24;
    /** Drawn out to here (blocks); the tracking range brings it in from about 128. */
    static final double RANGE = 160;
    /** Birds and insects hush within this radius while a rift is open (what is left of them: DEPTH). */
    static final float HUSH_RADIUS = 40F, HUSH_DEPTH = 0.2F;

    /** Violet glow, magenta body, white-hot core (linear-ish sRGB). */
    private static final float GR = 0.62F, GG = 0.36F, GB = 1.00F;
    private static final float BR = 1.00F, BG = 0.48F, BB = 1.00F;

    // ---- what is open ------------------------------------------------------------------------------------------------

    private static final List<RiftEntity> RIFTS = new ArrayList<>();

    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof RiftEntity r) RIFTS.add(r);
    }

    static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof RiftEntity r) RIFTS.remove(r);
    }

    static void clear() {
        RIFTS.clear();
    }

    /** Client tick: keep the hush round every open rift (renewed before it eases back). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RIFTS.isEmpty() || mc.level.getGameTime() % 40 != 0) return;
        RIFTS.removeIf(RiftEntity::isRemoved);
        for (RiftEntity r : RIFTS) {
            if (r.closingAge >= 0) continue;
            Soundscape.hush(r, r.position().add(0, 2, 0), HUSH_RADIUS, HUSH_DEPTH, 60);
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), FLAT = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static final float[] COS = new float[RING + 1], SIN = new float[RING + 1];
    private static final float[] NX = new float[NODES + 1], NW = new float[NODES + 1];
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    static {
        for (int i = 0; i <= RING; i++) {
            COS[i] = Mth.cos(i * Mth.TWO_PI / RING);
            SIN[i] = Mth.sin(i * Mth.TWO_PI / RING);
        }
    }

    private static VertexConsumer vc;
    private static double cx, cy, cz;
    private static float pt;
    private static ClientLevel level;

    static void submit(SubmitCustomGeometryEvent e) {
        if (RIFTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        level = mc.level;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        // the crack turns about its own upright to face you: the camera's right, laid flat
        FLAT.set(RIGHT.x, 0, RIGHT.z);
        if (FLAT.lengthSquared() < 1e-4F) FLAT.set(1, 0, 0);
        FLAT.normalize();
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light(), DRAW);
    }

    private static final SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, consumer) -> {
        try {
            vc = consumer;
            for (int i = 0; i < RIFTS.size(); i++) {
                RiftEntity r = RIFTS.get(i);
                if (!r.isRemoved()) rift(r);
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[anomaly] rift glow failed", ex);
            RIFTS.clear();
        } finally {
            vc = null;
        }
    };

    private static void rift(RiftEntity r) {
        double x = Mth.lerp(pt, r.xo, r.getX()), y = Mth.lerp(pt, r.yo, r.getY()), z = Mth.lerp(pt, r.zo, r.getZ());
        double dx = x - cx, dy = y - cy, dz = z - cz;
        if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) return;
        float age = r.tickCount + pt;
        float t = age / 20F;
        float open = Mth.clamp(age / 30F, 0F, 1F);
        open = 1F - (1F - open) * (1F - open) * (1F - open);
        if (r.closingAge >= 0) open = Math.max(0F, 1F - (r.closingAge + pt) / 20F);
        if (open <= 0.01F) return;
        float health = r.hitsLeft() / (float) Math.max(1, r.maxHits());
        float flash = r.flash > 0 ? Math.max(0F, (r.flash - pt) / 6F) : 0F;
        // the first second: a flash as it tears, then the steady breath
        float birth = r.closingAge < 0 ? Math.max(0F, 1F - age / 24F) : 0F;
        float breath = 0.9F + 0.1F * Mth.sin(t * 2.3F) + 0.04F * Mth.sin(t * 11.7F + r.getId());
        float power = open * breath * (1F + 0.8F * flash + 1.2F * birth);
        float px = (float) dx, py = (float) dy, pz = (float) dz;
        int seed = r.getId() * 7919;

        // the pool of violet on the ground under it, and the ring that runs out as it opens
        float gy = groundY(r, y) - (float) cy + 0.04F;
        pool(px, gy, pz, POOL * (0.6F + 0.4F * open), POOL_A * power);
        if (birth > 0F) ring(px, gy + 0.02F, pz, 2F + 16F * (1F - birth), 1.6F, 0.5F * birth);

        // the column into the sky: thin and faint, but against the sky it reads from far off
        column(px, py + FOOT, pz, COLUMN * open, 0.22F * open, 0.16F * power);
        column(px, py + FOOT, pz, COLUMN * 0.6F * open, 0.9F * open, 0.05F * power);

        // the halo round the crack's middle
        float mid = py + FOOT + HEIGHT * 0.5F * (0.35F + 0.65F * open);
        sprite(px, mid, pz, HALO * (0.55F + 0.45F * open), col(GR, GG, GB, 0.30F * power), col(GR, GG, GB, 0F));
        sprite(px, mid, pz, HALO * 0.42F * open, col(BR, BG, BB, 0.40F * power), col(BR, BG, BB, 0F));

        // the crack: its nodes (a jagged line whose edges crawl), then three ribbons over them
        float h = HEIGHT * (0.35F + 0.65F * open);
        for (int i = 0; i <= NODES; i++) {
            float v = i / (float) NODES;
            float j = hash(seed + i) * 2F - 1F;
            float crawl = 0.12F * Mth.sin(t * 3.1F + i * 1.7F) + 0.06F * Mth.sin(t * 7.3F + i * 2.9F);
            NX[i] = (i == 0 || i == NODES) ? 0F : (j * 0.42F + crawl) * open;
            float lens = (float) Math.pow(Mth.sin(Mth.PI * v), 0.7);
            NW[i] = HALF_W * lens * open * (0.75F + 0.25F * health) * (0.85F + 0.15F * Mth.sin(t * 5F + i));
        }
        ribbon(px, py + FOOT, pz, h, 2.8F, col(GR, GG, GB, 0.55F * power), col(GR, GG, GB, 0F));
        ribbon(px, py + FOOT, pz, h, 1.0F, col(BR, BG, BB, 0.85F * power), col(BR, BG, BB, 0.05F));
        ribbon(px, py + FOOT, pz, h, 0.3F, col(1F, 0.95F, 1F, Math.min(1F, 1.0F * power)), col(1F, 0.85F, 1F, 0.3F * power));
        // side cracks: short, thin, flickering in and out
        for (int k = 0; k < 4; k++) {
            float on = Mth.clamp(Mth.sin(t * (1.3F + k * 0.4F) + k * 2.1F) * 2F, 0F, 1F) * open;
            if (on <= 0.02F) continue;
            int n = 2 + (int) (hash(seed + 50 + k) * (NODES - 4));
            float side = hash(seed + 60 + k) > 0.5F ? 1F : -1F;
            float len = 0.8F + 1.2F * hash(seed + 70 + k);
            float rise = (hash(seed + 80 + k) - 0.3F) * 1.2F;
            float ax = NX[n], ay = py + FOOT + h * n / NODES;
            branch(px, ay, pz, ax, side * len, rise, 0.06F * on, col(BR, BG, BB, 0.8F * on * power));
        }

        // the core at its foot (the thing you strike): a hot point with its own small halo
        float core = (0.35F + 0.3F * health) * open * (1F + 0.6F * flash);
        sprite(px, py + 0.6F, pz, core * 3.2F, col(BR, BG, BB, 0.45F * power), col(BR, BG, BB, 0F));
        sprite(px, py + 0.6F, pz, core, col(1F, 1F, 1F, 0.95F), col(1F, 0.8F, 1F, 0F));

        // motes drawn in spirals into the crack
        for (int i = 0; i < MOTES; i++) {
            float ph = frac(t * (0.18F + 0.07F * hash(seed + 100 + i)) + hash(seed + 200 + i));
            float rad = 5.0F * (1F - ph) * (0.6F + 0.4F * hash(seed + 300 + i));
            float ang = i * 2.39996F + ph * 5.5F;
            float my = py + FOOT + h * (0.15F + 0.7F * hash(seed + 400 + i)) - 1.2F * (1F - ph);
            float a = 4F * ph * (1F - ph) * open;
            sprite(px + Mth.cos(ang) * rad, my, pz + Mth.sin(ang) * rad, 0.07F + 0.06F * ph,
                    col(1F, 0.85F, 1F, 0.9F * a), col(GR, GG, GB, 0F));
        }
    }

    /** The top of the ground under the rift (a few blocks down from its core). */
    private static float groundY(RiftEntity r, double y) {
        int bx = Mth.floor(r.getX()), by = Mth.floor(y), bz = Mth.floor(r.getZ());
        for (int d = 0; d <= 4; d++) {
            BlockState s = level.getBlockState(M.set(bx, by - d, bz));
            if (!s.isAir() && s.isSolidRender()) return by - d + 1;
        }
        return (float) y;
    }

    /** One ribbon along the crack's nodes, {@code width} × each node's half-width, its middle {@code c}, edges {@code e}. */
    private static void ribbon(float x, float y0, float z, float h, float width, int c, int e) {
        for (int i = 0; i < NODES; i++) {
            float ya = y0 + h * i / NODES, yb = y0 + h * (i + 1) / NODES;
            float xa = NX[i], xb = NX[i + 1];
            float wa = NW[i] * width, wb = NW[i + 1] * width;
            // two quads per segment: left edge to middle, middle to right edge
            quad(x, z, xa - wa, ya, xb - wb, yb, xb, yb, xa, ya, e, e, c, c);
            quad(x, z, xa, ya, xb, yb, xb + wb, yb, xa + wa, ya, c, c, e, e);
        }
    }

    /** A side crack from a node: out sideways and a little up, thinning to nothing. */
    private static void branch(float x, float y, float z, float from, float out, float rise, float w, int c) {
        int e = c & 0x00FFFFFF;
        float mx = from + out * 0.5F, my = y + rise * 0.5F + 0.15F;
        quad(x, z, from, y - w, mx, my - w * 0.5F, mx, my + w * 0.5F, from, y + w, c, c, c, c);
        quad(x, z, mx, my - w * 0.5F, from + out, y + rise, from + out, y + rise, mx, my + w * 0.5F, c, e, e, c);
    }

    /** A quad in the crack's plane: (u, y) pairs, u along the flat camera right. */
    private static void quad(float x, float z, float u0, float y0, float u1, float y1, float u2, float y2, float u3, float y3,
                             int c0, int c1, int c2, int c3) {
        vc.addVertex(x + FLAT.x * u0, y0, z + FLAT.z * u0).setColor(c0);
        vc.addVertex(x + FLAT.x * u1, y1, z + FLAT.z * u1).setColor(c1);
        vc.addVertex(x + FLAT.x * u2, y2, z + FLAT.z * u2).setColor(c2);
        vc.addVertex(x + FLAT.x * u3, y3, z + FLAT.z * u3).setColor(c3);
    }

    /** A thin vertical beam facing the camera, fading to nothing at its top. */
    private static void column(float x, float y, float z, float h, float w, float a) {
        if (a <= 0.003F || h <= 0.1F) return;
        int c = col(GR, GG, GB, a), top = col(GR, GG, GB, 0F);
        float lo = y + h * 0.12F;
        int e = col(GR, GG, GB, 0F);
        // a short fade in above the crack's foot, then the long fade out
        quad(x, z, -w, y, w, y, w, lo, -w, lo, e, e, c, c);
        quad(x, z, -w, lo, w, lo, w, y + h, -w, y + h, c, c, top, top);
    }

    /** A soft disc on the ground, strong in the middle, easing out to nothing. */
    private static void pool(float x, float y, float z, float radius, float s) {
        if (s <= 0.003F) return;
        disc(x, y, z, 0F, radius * 0.25F, s, s * 0.9F);
        disc(x, y, z, radius * 0.25F, radius * 0.55F, s * 0.9F, s * 0.45F);
        disc(x, y, z, radius * 0.55F, radius * 0.8F, s * 0.45F, s * 0.12F);
        disc(x, y, z, radius * 0.8F, radius, s * 0.12F, 0F);
    }

    /** A ring along the ground (the opening's shock), {@code band} wide. */
    private static void ring(float x, float y, float z, float radius, float band, float s) {
        disc(x, y, z, Math.max(0F, radius - band), radius, 0F, s);
        disc(x, y, z, radius, radius + band * 0.5F, s, 0F);
    }

    private static void disc(float x, float y, float z, float r0, float r1, float a0, float a1) {
        int c0 = col(GR, GG, GB, a0), c1 = col(GR, GG, GB, a1);
        for (int i = 0; i < RING; i++) {
            vc.addVertex(x + COS[i] * r0, y, z + SIN[i] * r0).setColor(c0);
            vc.addVertex(x + COS[i] * r1, y, z + SIN[i] * r1).setColor(c1);
            vc.addVertex(x + COS[i + 1] * r1, y, z + SIN[i + 1] * r1).setColor(c1);
            vc.addVertex(x + COS[i + 1] * r0, y, z + SIN[i + 1] * r0).setColor(c0);
        }
    }

    /** A soft disc facing the camera: centre colour fading to the edge, as an eight-sided fan of quads. */
    private static void sprite(float x, float y, float z, float r, int inner, int outer) {
        for (int i = 0; i < 8; i++) {
            int a = i * RING / 8, b = (i + 1) * RING / 8;
            float ax = (RIGHT.x * COS[a] + UP.x * SIN[a]) * r, ay = (RIGHT.y * COS[a] + UP.y * SIN[a]) * r, az = (RIGHT.z * COS[a] + UP.z * SIN[a]) * r;
            float bx = (RIGHT.x * COS[b] + UP.x * SIN[b]) * r, by = (RIGHT.y * COS[b] + UP.y * SIN[b]) * r, bz = (RIGHT.z * COS[b] + UP.z * SIN[b]) * r;
            vc.addVertex(x, y, z).setColor(inner);
            vc.addVertex(x + ax, y + ay, z + az).setColor(outer);
            vc.addVertex(x + bx, y + by, z + bz).setColor(outer);
            vc.addVertex(x, y, z).setColor(inner);
        }
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }

    private static int col(float r, float g, float b, float a) {
        // additive: the colour is pre-scaled by its strength (the lightning blend takes alpha as the source weight)
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (int) (r * 255F) << 16 | (int) (g * 255F) << 8 | (int) (b * 255F);
    }
}
