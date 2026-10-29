package dev.awaken.caves.client;

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
import dev.awaken.caves.CaveRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * The regions' drawn light (SPEC §42.8): light that shows how big a cave is without placing a light block, so nothing is
 * relit, re-meshed or kept from spawning. The Pale Halls' high roofs are a field of glow-worm stars, pale blue-green
 * points that brighten and dim; over the Drowned Galleries' lakes the water throws a slow, wavering teal light on the
 * roof; over the Black Vaults' lava a red heat hangs in the air. Found a few columns a tick round the camera, faded in
 * and out, drawn to 48 blocks in one additive pass (as the city's lanterns are, {@code citizens.client.CityGlow}), so the
 * brightest feed Lens's bloom. Client only; nothing allocated per frame or tick.
 */
final class DrawnLight {
    private DrawnLight() {}

    // ---- the pipeline (additive, depth-tested, no depth written) ------------------------------------------------------

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline LIGHT = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/cave_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final OitPipelineSet LIGHT_OIT = OitPipelineSet.builder(Awaken.id("cave_glow"),
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
        if (type == null) type = RenderType.create("awaken_cave_glow", RenderSetup.builder(LIGHT).setOitPipelines(LIGHT_OIT).createRenderSetup());
        return type;
    }

    // ---- the numbers ------------------------------------------------------------------------------------------------

    static final int STAR = 0, LAKE = 1, HEAT = 2;
    /** At most this many lights at once; columns looked at a tick; how far round the camera (blocks). */
    static final int CAP = 400, COLUMNS = 6, REACH = 40, DRAW = 48;
    /** A roof is high enough for glow-worm stars this far over its floor; lakes and lava are looked for this far down. */
    static final int HIGH_ROOF = 6, DOWN = 24, UP = 24;
    /** A star's life (ticks, at least, then up to as much again); a lake's light and the heat live longer. */
    static final int STAR_LIFE = 300, GLOW_LIFE = 400, FADE = 60;

    static final int[] kind = new int[CAP], born = new int[CAP], life = new int[CAP];
    static final double[] x = new double[CAP], y = new double[CAP], z = new double[CAP];
    static final float[] phase = new float[CAP], size = new float[CAP];
    private static int live;
    private static int now;
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
        live = 0;
    }

    // ---- once a tick ------------------------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        now++;
        Vec3 c = mc.gameRenderer.mainCamera().position();
        CaveRegion r = CavesClientConfig.drawnLight() ? RegionClient.here : null;
        // the region's lights fade once you've left it; far ones go at once
        for (int i = 0; i < CAP; i++) {
            if (kind[i] < 0) continue;
            double dx = x[i] - c.x, dy = y[i] - c.y, dz = z[i] - c.z;
            if (dx * dx + dy * dy + dz * dz > (DRAW + 4) * (DRAW + 4) || now - born[i] >= life[i]) {
                kind[i] = -1;
                live--;
            } else if (r == null || !owns(r, kind[i])) {
                life[i] = Math.min(life[i], now - born[i] + FADE / 2);
            }
        }
        if (r != CaveRegion.PALE_HALLS && r != CaveRegion.DROWNED_GALLERIES && r != CaveRegion.BLACK_VAULTS) return;
        int cx = Mth.floor(c.x), cy = Mth.floor(c.y), cz = Mth.floor(c.z);
        for (int s = 0; s < COLUMNS && live < CAP; s++) {
            double a = R.nextDouble() * Mth.TWO_PI, d = Math.sqrt(R.nextDouble()) * REACH;
            int bx = cx + Mth.floor(Math.cos(a) * d), bz = cz + Mth.floor(Math.sin(a) * d);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            switch (r) {
                case PALE_HALLS -> stars(level, bx, cy, bz);
                case DROWNED_GALLERIES -> lake(level, bx, cy, bz);
                case BLACK_VAULTS -> heat(level, bx, cy, bz);
                default -> { }
            }
        }
    }

    private static boolean owns(CaveRegion r, int k) {
        return k == STAR ? r == CaveRegion.PALE_HALLS : k == LAKE ? r == CaveRegion.DROWNED_GALLERIES : r == CaveRegion.BLACK_VAULTS;
    }

    /** The first air cell at or near the camera's height in a column (up to 4 either way), or MIN_VALUE. */
    private static int open(ClientLevel level, int bx, int cy, int bz) {
        for (int k = 0; k <= 8; k++) {
            int yy = cy + ((k & 1) == 0 ? k / 2 : -(k + 1) / 2);
            if (level.getBlockState(M.set(bx, yy, bz)).isAir()) return yy;
        }
        return Integer.MIN_VALUE;
    }

    /** A high roof over open floor: two or three glow-worm stars on its underside. */
    private static void stars(ClientLevel level, int bx, int cy, int bz) {
        int o = open(level, bx, cy, bz);
        if (o == Integer.MIN_VALUE) return;
        int roof = Integer.MIN_VALUE, floor = Integer.MIN_VALUE;
        for (int yy = o; yy <= o + UP; yy++) if (solid(level.getBlockState(M.set(bx, yy, bz)))) { roof = yy; break; }
        for (int yy = o; yy >= o - DOWN; yy--) if (!level.getBlockState(M.set(bx, yy, bz)).isAir()) { floor = yy; break; }
        if (roof == Integer.MIN_VALUE || floor == Integer.MIN_VALUE || roof - floor - 1 < HIGH_ROOF) return;
        int n = 2 + R.nextInt(2);
        for (int j = 0; j < n; j++) {
            add(STAR, bx + 0.1 + R.nextDouble() * 0.8, roof - 0.02, bz + 0.1 + R.nextDouble() * 0.8, STAR_LIFE + R.nextInt(STAR_LIFE),
                    0.8F + R.nextFloat() * 0.5F);
        }
    }

    /** Still water under the column and a roof over it: the lake's light wavering on the roof. */
    private static void lake(ClientLevel level, int bx, int cy, int bz) {
        int o = open(level, bx, cy, bz);
        if (o == Integer.MIN_VALUE) return;
        int water = Integer.MIN_VALUE, roof = Integer.MIN_VALUE;
        for (int yy = o; yy >= o - DOWN; yy--) {
            BlockState s = level.getBlockState(M.set(bx, yy, bz));
            if (s.isAir()) continue;
            if (s.getFluidState().is(FluidTags.WATER)) water = yy;
            break;
        }
        if (water == Integer.MIN_VALUE) return;
        for (int yy = water + 1; yy <= water + UP; yy++) if (solid(level.getBlockState(M.set(bx, yy, bz)))) { roof = yy; break; }
        if (roof == Integer.MIN_VALUE || near(LAKE, bx + 0.5, roof, bz + 0.5, 2.5)) return;
        add(LAKE, bx + 0.5, roof - 0.03, bz + 0.5, GLOW_LIFE + R.nextInt(GLOW_LIFE), 1.6F + R.nextFloat() * 1.2F);
    }

    /** Lava under the column: its heat hanging over it, red into the dark. */
    private static void heat(ClientLevel level, int bx, int cy, int bz) {
        int o = open(level, bx, cy, bz);
        if (o == Integer.MIN_VALUE) return;
        for (int yy = o; yy >= o - DOWN * 2; yy--) {
            BlockState s = level.getBlockState(M.set(bx, yy, bz));
            if (s.isAir()) continue;
            if (s.getFluidState().is(FluidTags.LAVA) && !near(HEAT, bx + 0.5, yy + 1.6, bz + 0.5, 4)) {
                add(HEAT, bx + 0.5, yy + 1.4 + R.nextDouble() * 1.2, bz + 0.5, GLOW_LIFE + R.nextInt(GLOW_LIFE), 2.6F + R.nextFloat() * 1.6F);
            }
            return;
        }
    }

    private static boolean solid(BlockState s) {
        return !s.isAir() && s.isSolidRender();
    }

    private static boolean near(int k, double px, double py, double pz, double r) {
        for (int i = 0; i < CAP; i++) {
            if (kind[i] != k) continue;
            double dx = x[i] - px, dy = y[i] - py, dz = z[i] - pz;
            if (dx * dx + dy * dy + dz * dz < r * r) return true;
        }
        return false;
    }

    private static void add(int k, double px, double py, double pz, int lifetime, float sz) {
        for (int i = 0; i < CAP; i++) {
            if (kind[i] >= 0) continue;
            kind[i] = k;
            x[i] = px;
            y[i] = py;
            z[i] = pz;
            born[i] = now;
            life[i] = lifetime;
            size[i] = sz;
            phase[i] = R.nextFloat() * Mth.TWO_PI;
            live++;
            return;
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static double cx, cy, cz;
    private static float t;
    private static VertexConsumer vc;
    private static float rx, ry, rz, ux, uy, uz;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final int RING = 8;
    private static final float[] COS = new float[RING + 1], SIN = new float[RING + 1];

    static {
        for (int i = 0; i <= RING; i++) {
            COS[i] = Mth.cos(i * Mth.TWO_PI / RING);
            SIN[i] = Mth.sin(i * Mth.TWO_PI / RING);
        }
    }

    static void submit(SubmitCustomGeometryEvent e) {
        if (live == 0) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        t = now + state.worldPartialTicks;
        // the camera's own right and up, for lights that face it
        CAM.set(state.cameraRenderState.orientation);
        V.set(1, 0, 0).rotate(CAM);
        rx = V.x;
        ry = V.y;
        rz = V.z;
        V.set(0, 1, 0).rotate(CAM);
        ux = V.x;
        uy = V.y;
        uz = V.z;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light(), LIGHTS);
    }

    private static final SubmitNodeCollector.CustomGeometryRenderer LIGHTS = (pose, consumer) -> {
        try {
            vc = consumer;
            for (int i = 0; i < CAP; i++) {
                int k = kind[i];
                if (k < 0) continue;
                float px = (float) (x[i] - cx), py = (float) (y[i] - cy), pz = (float) (z[i] - cz);
                float d = Mth.sqrt(px * px + py * py + pz * pz);
                if (d > DRAW) continue;
                float age = t - born[i], fade = Mth.clamp(Math.min(age, life[i] - age) / FADE, 0F, 1F);
                // nothing pops in at the edge of the reach
                fade *= Mth.clamp((DRAW - d) / 8F, 0F, 1F);
                if (fade <= 0.01F) continue;
                float ph = phase[i];
                switch (k) {
                    case STAR -> {
                        // a point that stays a few pixels across however far, brightening and dimming slowly
                        float tw = 0.65F + 0.35F * Mth.sin(t * 0.05F + ph);
                        float r = (0.035F + d * 0.0022F) * size[i];
                        sprite(px, py, pz, r * 2.6F, col(0.42F, 0.86F, 0.95F, 0.28F * tw * fade), 0);
                        sprite(px, py, pz, r, col(0.78F, 1F, 1F, 0.9F * tw * fade), col(0.5F, 0.92F, 1F, 0F));
                    }
                    case LAKE -> {
                        // the water's light on the roof: two soft discs drifting against each other, slowly
                        float w1 = 0.5F + 0.5F * Mth.sin(t * 0.031F + ph), w2 = 0.5F + 0.5F * Mth.sin(t * 0.047F + ph * 1.7F);
                        float s = size[i];
                        disc(px + 0.4F * Mth.sin(t * 0.013F + ph), py, pz, s, col(0.3F, 0.85F, 0.75F, (0.07F + 0.07F * w1) * fade));
                        disc(px, py, pz + 0.4F * Mth.cos(t * 0.017F + ph), s * 0.7F, col(0.45F, 0.95F, 0.85F, (0.05F + 0.08F * w2) * fade));
                    }
                    case HEAT -> {
                        float f = 0.85F + 0.1F * Mth.sin(t * 0.11F + ph) + 0.05F * Mth.sin(t * 0.37F + ph * 2.1F);
                        sprite(px, py, pz, size[i], col(1F, 0.36F, 0.12F, 0.16F * f * fade), 0);
                        sprite(px, py, pz, size[i] * 0.45F, col(1F, 0.55F, 0.2F, 0.14F * f * fade), 0);
                    }
                    default -> { }
                }
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[caves] drawn light failed", ex);
            clear();
        }
    };

    /** A soft disc facing the camera: centre colour fading to the edge, a fan of quads. */
    private static void sprite(float x0, float y0, float z0, float r, int inner, int outer) {
        for (int i = 0; i < RING; i++) {
            float ax = (rx * COS[i] + ux * SIN[i]) * r, ay = (ry * COS[i] + uy * SIN[i]) * r, az = (rz * COS[i] + uz * SIN[i]) * r;
            float bx = (rx * COS[i + 1] + ux * SIN[i + 1]) * r, by = (ry * COS[i + 1] + uy * SIN[i + 1]) * r, bz = (rz * COS[i + 1] + uz * SIN[i + 1]) * r;
            vc.addVertex(x0, y0, z0).setColor(inner);
            vc.addVertex(x0 + ax, y0 + ay, z0 + az).setColor(outer);
            vc.addVertex(x0 + bx, y0 + by, z0 + bz).setColor(outer);
            vc.addVertex(x0, y0, z0).setColor(inner);
        }
    }

    /** A soft disc lying flat (on a roof): centre colour fading to nothing at the edge. */
    private static void disc(float x0, float y0, float z0, float r, int inner) {
        for (int i = 0; i < RING; i++) {
            vc.addVertex(x0, y0, z0).setColor(inner);
            vc.addVertex(x0 + COS[i] * r, y0, z0 + SIN[i] * r).setColor(0);
            vc.addVertex(x0 + COS[i + 1] * r, y0, z0 + SIN[i + 1] * r).setColor(0);
            vc.addVertex(x0, y0, z0).setColor(inner);
        }
    }

    private static int col(float r, float g, float b, float a) {
        // additive: the colour is pre-scaled by its strength (the lightning blend takes alpha as the source weight)
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (int) (r * 255F) << 16 | (int) (g * 255F) << 8 | (int) (b * 255F);
    }
}
