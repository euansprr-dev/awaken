package dev.awaken.citizens.client;

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
import dev.awaken.citizens.CitizenBody;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.citizens.block.StreetLampBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
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
import java.util.Arrays;
import java.util.List;

/**
 * The city's warm light at night, drawn instead of placed (M4 B3b; docs/perf/CITY-FPS.md, finding 3): a carried lantern
 * (the Watch, the lamplighter, a late walker) is a hot core, a soft halo and a pool of light on the stones round the
 * bearer's feet that moves with them, and every lit street lamp throws a wider pool under itself. Nothing touches a
 * block, so nothing is relit or re-meshed: the old lanterns moved up to 12 light blocks every 5 ticks.
 * <p>One additive pass (the game's lightning shaders, depth-tested, no depth written), so the stones brighten where the
 * light falls and the near-white cores feed Lens's bloom. Other city life (a cart's lamp, a boat's lantern) adds its own
 * lights through {@link #source}. Nothing is allocated per frame.
 */
public final class CityGlow {
    private CityGlow() {}

    // ---- the light's pipeline (additive, depth-tested, no depth written) -----------------------------------------------

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline LIGHT = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/city_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final OitPipelineSet LIGHT_OIT = OitPipelineSet.builder(Awaken.id("city_glow"),
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
        if (type == null) type = RenderType.create("awaken_city_glow", RenderSetup.builder(LIGHT).setOitPipelines(LIGHT_OIT).createRenderSetup());
        return type;
    }

    // ---- the numbers ----------------------------------------------------------------------------------------------

    /** A carried lantern: pool radius and strength, halo size; a street lamp's pool is wider and softer. */
    static final float LANTERN_POOL = 4.6F, LANTERN_POOL_A = 0.30F, LANTERN_HALO = 0.85F, LANTERN_CORE = 0.16F;
    static final float LAMP_POOL = 7.5F, LAMP_POOL_A = 0.24F, LAMP_HALO = 1.25F, LAMP_CORE = 0.22F;
    /** Lights further than this from the camera aren't drawn (the fog has them by then). */
    static final double RANGE = 88;
    /** Warm candle light (linear-ish sRGB): the pool's middle is warmer and dimmer than the flame. */
    private static final float PR = 1.00F, PG = 0.66F, PB = 0.30F, HR = 1.00F, HG = 0.78F, HB = 0.46F;

    /** Other life's lights: called while the pass draws (positions at this frame's partial tick). */
    public interface Source {
        void glows(CityGlow.Out out, float partial, double cx, double cy, double cz);
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    public static void source(Source s) {
        if (!SOURCES.contains(s)) SOURCES.add(s);
    }

    // ---- what carries a lantern -----------------------------------------------------------------------------------

    private static final List<CitizenBody> BODIES = new ArrayList<>();

    static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof CitizenBody b) BODIES.add(b);
    }

    static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof CitizenBody b) BODIES.remove(b);
    }

    /** Lamp heads (CitizenFx's kind 5) and the ground under each, found once its chunk is in. */
    private static long[] lamps = new long[0];
    private static int[] lampGround = new int[0];
    private static boolean lampsDirty = true;

    static void emittersChanged() {
        lampsDirty = true;
    }

    private static void refreshLamps() {
        lampsDirty = false;
        long[] pos = CitizenFx.positions();
        byte[] kind = CitizenFx.kinds();
        int n = 0;
        for (byte k : kind) if (k == 5) n++;
        lamps = new long[n];
        lampGround = new int[n];
        Arrays.fill(lampGround, Integer.MIN_VALUE);
        int j = 0;
        for (int i = 0; i < pos.length && i < kind.length; i++) if (kind[i] == 5) lamps[j++] = pos[i];
    }

    /** True while a body carries a lit lantern (client: its synced off-hand item). */
    static boolean carries(CitizenBody b) {
        return !b.isRemoved() && b.getOffhandItem().is(CitizensContent.HAND_LANTERN.get());
    }

    static void clear() {
        BODIES.clear();
        lamps = new long[0];
        lampGround = new int[0];
        lampsDirty = true;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private static final Out OUT = new Out();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (lampsDirty) refreshLamps();
        if (BODIES.isEmpty() && lamps.length == 0 && SOURCES.isEmpty()) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        float pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        float t = (level.getGameTime() % 24000L + pt) / 20F;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light(), (pose, vc) -> {
            try {
                OUT.begin(vc, cam.x, cam.y, cam.z, t);
                for (CitizenBody b : BODIES) if (carries(b)) lantern(b, pt);
                lamps(level);
                for (Source s : SOURCES) s.glows(OUT, pt, cam.x, cam.y, cam.z);
            } catch (RuntimeException ex) {
                Awaken.LOG.error("[citizens] city glow failed", ex);
                SOURCES.clear();
            }
        });
    }

    /** The lantern hangs from the bearer's off hand (left): swung by the walk, low at the hip. */
    private static void lantern(CitizenBody b, float pt) {
        double x = Mth.lerp(pt, b.xo, b.getX()), y = Mth.lerp(pt, b.yo, b.getY()), z = Mth.lerp(pt, b.zo, b.getZ());
        if (OUT.far(x, y, z)) return;
        float yaw = Mth.rotLerp(pt, b.yBodyRotO, b.yBodyRot) * Mth.DEG_TO_RAD;
        float swing = Mth.cos(b.walkAnimation.position(pt) * 0.6662F) * 1.1F * b.walkAnimation.speed(pt) * 0.5F;
        float arm = 0.6F, s = b.getScale();
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
        double fwd = Mth.sin(swing) * arm * s + 0.06, side = 0.36 * s, up = (1.36 - Mth.cos(swing) * arm - 0.16) * s;
        double hx = x + fx * fwd + lx * side, hy = y + up, hz = z + fz * fwd + lz * side;
        float f = OUT.flicker(b.getId());
        OUT.pool(hx, y + 0.03, hz, LANTERN_POOL, LANTERN_POOL_A * f);
        OUT.halo(hx, hy, hz, LANTERN_HALO, LANTERN_CORE, f);
    }

    /** Street lamps that are lit (the lamplighter's work): a halo round the lantern head and the pool round the post. */
    private static void lamps(ClientLevel level) {
        for (int k = 0; k < lamps.length; k++) {
            long p = lamps[k];
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            if (OUT.far(x + 0.5, y, z + 0.5)) continue;
            BlockState st = level.getBlockState(M.set(x, y, z));
            if (!st.is(CitizensContent.STREET_LAMP.get()) || st.getValue(StreetLampBlock.LAMP) != StreetLampBlock.Lamp.LIT) continue;
            if (lampGround[k] == Integer.MIN_VALUE) lampGround[k] = ground(level, x, y, z);
            float f = OUT.flicker(k * 7919);
            OUT.halo(x + 0.5, y + 0.45, z + 0.5, LAMP_HALO, LAMP_CORE, f);
            OUT.pool(x + 0.5, lampGround[k] + 0.03, z + 0.5, LAMP_POOL, LAMP_POOL_A * f);
        }
    }

    /** The top of the first solid block under a lamp's head (its post's foot), within 8. */
    private static int ground(ClientLevel level, int x, int y, int z) {
        for (int d = 1; d <= 8; d++) {
            BlockState s = level.getBlockState(M.set(x, y - d, z));
            if (!s.isAir() && s.isSolidRender()) return y - d + 1;
        }
        return y - 4;
    }

    /** Where lights are written: camera-relative quads, in this frame's camera basis. */
    public static final class Out {
        private VertexConsumer vc;
        private double cx, cy, cz;
        private float t;
        private static final int RING = 20;
        private static final float[] COS = new float[RING + 1], SIN = new float[RING + 1];

        static {
            for (int i = 0; i <= RING; i++) {
                COS[i] = Mth.cos(i * Mth.TWO_PI / RING);
                SIN[i] = Mth.sin(i * Mth.TWO_PI / RING);
            }
        }

        void begin(VertexConsumer vc, double cx, double cy, double cz, float t) {
            this.vc = vc;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.t = t;
        }

        public boolean far(double x, double y, double z) {
            double dx = x - cx, dy = y - cy, dz = z - cz;
            return dx * dx + dy * dy + dz * dz > RANGE * RANGE;
        }

        /** A flame's unsteady breath: 0.86-1.0, its own rhythm per seed. */
        public float flicker(int seed) {
            float a = seed * 0.37F;
            return 0.93F + 0.045F * Mth.sin(t * 9.1F + a) + 0.025F * Mth.sin(t * 23.7F + a * 2.3F);
        }

        /**
         * A pool of light on the ground: a disc whose strength falls off smoothly to nothing at its edge (the bright
         * middle under the flame, then a long soft tail), in rings so the falloff reads round, not as a cone.
         */
        public void pool(double x, double y, double z, float radius, float strength) {
            if (strength <= 0.003F) return;
            float px = (float) (x - cx), py = (float) (y - cy), pz = (float) (z - cz);
            // ring radii and strengths: the inverse-square-ish falloff of a point light over a floor
            ring(px, py, pz, 0F, radius * 0.18F, strength, strength * 0.78F);
            ring(px, py, pz, radius * 0.18F, radius * 0.45F, strength * 0.78F, strength * 0.36F);
            ring(px, py, pz, radius * 0.45F, radius * 0.75F, strength * 0.36F, strength * 0.10F);
            ring(px, py, pz, radius * 0.75F, radius, strength * 0.10F, 0F);
        }

        private void ring(float x, float y, float z, float r0, float r1, float a0, float a1) {
            int c0 = col(PR, PG, PB, a0), c1 = col(PR, PG, PB, a1);
            for (int i = 0; i < RING; i++) {
                vc.addVertex(x + COS[i] * r0, y, z + SIN[i] * r0).setColor(c0);
                vc.addVertex(x + COS[i] * r1, y, z + SIN[i] * r1).setColor(c1);
                vc.addVertex(x + COS[i + 1] * r1, y, z + SIN[i + 1] * r1).setColor(c1);
                vc.addVertex(x + COS[i + 1] * r0, y, z + SIN[i + 1] * r0).setColor(c0);
            }
        }

        /** The flame: a near-white core inside a warm halo, both facing the camera. */
        public void halo(double x, double y, double z, float radius, float core, float f) {
            float px = (float) (x - cx), py = (float) (y - cy), pz = (float) (z - cz);
            sprite(px, py, pz, radius * f, col(HR, HG, HB, 0.34F * f), col(HR, HG, HB, 0F));
            sprite(px, py, pz, radius * 0.42F, col(HR, HG, HB, 0.42F * f), col(HR, HG, HB, 0F));
            sprite(px, py, pz, core, col(1F, 0.95F, 0.82F, 0.95F), col(1F, 0.86F, 0.6F, 0F));
        }

        /** A soft disc facing the camera: centre colour fading to the edge, as an eight-sided fan of quads. */
        private void sprite(float x, float y, float z, float r, int inner, int outer) {
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

        private static int col(float r, float g, float b, float a) {
            // additive: the colour is pre-scaled by its strength (the lightning blend takes alpha as the source weight)
            return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (int) (r * 255F) << 16 | (int) (g * 255F) << 8 | (int) (b * 255F);
        }
    }
}
