package dev.awaken.delve.client;

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
import dev.awaken.delve.DelveContent;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * The miner's lamp (PLAN 7.5): its light is drawn on the rock ahead of whoever wears it, never placed (no light
 * blocks, nothing relit). A fan of rays from the brow finds the faces in front; each lit face is drawn again over itself
 * with its own block quads (the same model variant the world chose there, so it lines up), additively and in the
 * lamp's warm colour, brightest at the beam's middle and near, gone at its edge and at {@link #RANGE}: the rock's own
 * texture comes up out of the dark. A soft additive glow (the city lanterns' pass, {@code citizens.client.CityGlow})
 * gives the beam its haze, and from outside the lamp is a small flame on the brow. A lode's glint shows where the beam
 * falls ({@link #lights}).
 */
public final class LampGlow {
    private LampGlow() {}

    // ---- pipelines: the glow (untextured, like CityGlow) and the lit rock (the block atlas, additive) ----------------

    private static final RenderPipeline.Snippet GLOW_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline GLOW = RenderPipeline.builder(GLOW_SNIPPET)
            .withLocation(Awaken.id("pipeline/delve_lamp"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final OitPipelineSet GLOW_OIT = OitPipelineSet.builder(Awaken.id("delve_lamp"),
            RenderPipeline.builder(GLOW_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();
    static final RenderPipeline LIT = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withLocation(Awaken.id("pipeline/delve_lamp_lit"))
            .build();
    private static RenderType glowType, litType;
    private static int glowOk = -1, litOk = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(GLOW);
        e.registerOitPipelineSet(GLOW_OIT);
        e.registerOptionalPipeline(LIT);
    }

    private static boolean compiled(RenderPipeline p) {
        try {
            return RenderSystem.getCompiledPipelineNullable(p) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static RenderType glow() {
        if (glowOk < 0) glowOk = compiled(GLOW) ? 1 : 0;
        if (glowOk == 0) return RenderTypes.lightning();
        if (glowType == null) glowType = RenderType.create("awaken_delve_lamp", RenderSetup.builder(GLOW).setOitPipelines(GLOW_OIT).createRenderSetup());
        return glowType;
    }

    /** The lit-rock pass, or null when its pipeline didn't compile (the glow alone then). */
    private static RenderType lit() {
        if (litOk < 0) litOk = compiled(LIT) ? 1 : 0;
        if (litOk == 0) return null;
        if (litType == null) litType = RenderType.create("awaken_delve_lamp_lit", RenderSetup.builder(LIT)
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS).createRenderSetup());
        return litType;
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The beam's reach (blocks), its full half-angle and the half-angle of its bright core; others' lamps within OTHERS. */
    static final double RANGE = 22, OTHERS = 40;
    static final float CONE_DEG = 24F, CORE_DEG = 9F;
    /** How bright the lit rock gets at the beam's heart, near (the texture times this, added). */
    static final float BRIGHT = 0.95F;
    /** The faces one wearer's beam may light in a frame. */
    static final int MAX_FACES = 260;
    /** Rays: one down the middle, a ring of six at a third of the cone, a ring of ten at its edge. */
    private static final float[][] RAYS = rays();
    /** Warm lamp light (a carbide flame through brass). */
    private static final float R = 1.00F, G = 0.80F, B = 0.56F;

    private static float[][] rays() {
        float[][] out = new float[17][];
        out[0] = new float[]{0, 0};
        for (int i = 0; i < 6; i++) out[1 + i] = new float[]{(float) (i * Math.PI / 3), CONE_DEG * 0.38F};
        for (int i = 0; i < 10; i++) out[7 + i] = new float[]{(float) (i * Math.PI / 5 + 0.2), CONE_DEG * 0.85F};
        return out;
    }

    static boolean wears(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(DelveContent.MINERS_LAMP.get());
    }

    /** Whether the local player's lamp beam falls on this block now (the glints read it). */
    static boolean lights(BlockPos b) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !wears(p)) return false;
        Vec3 eye = p.getEyePosition(), to = Vec3.atCenterOf(b).subtract(eye);
        double d = to.length();
        return d < RANGE && d > 0.5 && p.getViewVector(1F).dot(to.scale(1 / d)) > Math.cos(Math.toRadians(CONE_DEG));
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static double cx, cy, cz;
    private static float pt;
    private static ClientLevel level;
    // reused every frame: the lit faces (pos * 8 + face), the model parts, the variant's random
    private static final LongOpenHashSet FACES = new LongOpenHashSet();
    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.createThreadLocalInstance(0L);
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos(), N = new BlockPos.MutableBlockPos();
    private static final float[] A = new float[4];
    // the beam being drawn: its origin and axis
    private static double ox, oy, oz, ax, ay, az;

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        level = mc.level;
        if (level == null || mc.player == null) return;
        boolean any = false;
        for (Player p : level.players()) {
            if (wears(p) && p.distanceToSqr(mc.player) < OTHERS * OTHERS) {
                any = true;
                break;
            }
        }
        if (!any) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        RenderType rock = lit();
        if (rock != null) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), rock, DRAW_ROCK);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), glow(), DRAW_GLOW);
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_ROCK = (pose, vc) -> {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (level == null || mc.player == null) return;
            for (Player p : level.players()) if (wears(p) && p.distanceToSqr(mc.player) <= OTHERS * OTHERS) rock(vc, p);
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] lamp light failed", ex);
            litOk = 0;
        }
    };

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW_GLOW = (pose, vc) -> {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (level == null || mc.player == null) return;
            for (Player p : level.players()) {
                if (!wears(p) || p.distanceToSqr(mc.player) > OTHERS * OTHERS) continue;
                haze(vc, p);
                if (p != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson()) lampHalo(vc, p);
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] lamp glow failed", ex);
        }
    };

    /** Sets the beam's origin (the brow, a hand above the eye) and axis for a wearer. */
    private static void aim(Player p) {
        Vec3 eye = p.getEyePosition(pt), f = p.getViewVector(pt);
        ox = eye.x;
        oy = eye.y + 0.22;
        oz = eye.z;
        ax = f.x;
        ay = f.y;
        az = f.z;
    }

    /** The lit rock: the faces the beam finds (each ray's face and its eight neighbours on that face), drawn again lit. */
    private static void rock(VertexConsumer vc, Player p) {
        aim(p);
        FACES.clear();
        Vec3 f = new Vec3(ax, ay, az), from = new Vec3(ox, oy, oz);
        Vec3 side = new Vec3(-az, 0, ax);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(f).normalize();
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        int drawn = 0;
        for (float[] ray : RAYS) {
            double off = Math.toRadians(ray[1]);
            Vec3 dir = f.scale(Math.cos(off)).add(side.scale(Math.sin(off) * Math.cos(ray[0]))).add(up.scale(Math.sin(off) * Math.sin(ray[0])));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            Direction face = hit.getDirection();
            BlockPos b = hit.getBlockPos();
            // the face plane's two in-plane axes
            Direction u = face.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
            Direction w = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : face.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                M.set(b.getX() + u.getStepX() * i + w.getStepX() * j, b.getY() + u.getStepY() * i + w.getStepY() * j, b.getZ() + u.getStepZ() * i + w.getStepZ() * j);
                long key = M.asLong() * 8 + face.ordinal();
                if (!FACES.add(key)) continue;
                BlockState s = level.getBlockState(M);
                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                N.setWithOffset(M, face);
                if (level.getBlockState(N).isSolidRender()) continue;   // that face is buried
                drawn += face(vc, models.get(s), s, M, face);
                if (drawn >= MAX_FACES) return;
            }
        }
    }

    /** One face of one block, its own quads, lit: returns the quads drawn. */
    private static int face(VertexConsumer vc, BlockStateModel model, BlockState s, BlockPos pos, Direction face) {
        PARTS.clear();
        RANDOM.setSeed(s.getSeed(pos));
        model.collectParts(level, pos, s, RANDOM, PARTS);
        int n = 0;
        float nx = face.getStepX() * 0.0015F, ny = face.getStepY() * 0.0015F, nz = face.getStepZ() * 0.0015F;
        for (BlockStateModelPart part : PARTS) {
            for (BakedQuad q : part.getQuads(face)) {
                float[] a = A;
                boolean any = false;
                for (int v = 0; v < 4; v++) {
                    Vector3fc pv = q.position(v);
                    a[v] = light(pos.getX() + pv.x(), pos.getY() + pv.y(), pos.getZ() + pv.z());
                    any |= a[v] > 0.004F;
                }
                if (!any) continue;
                for (int v = 0; v < 4; v++) {
                    Vector3fc pv = q.position(v);
                    long uv = q.packedUV(v);
                    vc.addVertex((float) (pos.getX() + pv.x() + nx - cx), (float) (pos.getY() + pv.y() + ny - cy), (float) (pos.getZ() + pv.z() + nz - cz))
                            .setUv(UVPair.unpackU(uv), UVPair.unpackV(uv)).setColor(col(a[v] * BRIGHT));
                }
                n++;
            }
        }
        return n;
    }

    /** The beam's light at a point: its cone (bright core, soft edge) times its fall-off with distance. */
    private static float light(double x, double y, double z) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-3 || d > RANGE) return 0;
        double c = (dx * ax + dy * ay + dz * az) / d;
        double edge = Math.cos(Math.toRadians(CONE_DEG)), core = Math.cos(Math.toRadians(CORE_DEG));
        if (c <= edge) return 0;
        double t = Math.min(1, (c - edge) / (core - edge));
        double cone = 0.25 + 0.75 * t * t * (3 - 2 * t);
        double fall = 1 - d / RANGE;
        return (float) (cone * fall * Math.sqrt(fall) * Math.min(1, 0.6 + d / 6));
    }

    /** The beam's haze: a soft pool of warm glow on each face a ray hits (the air lit between you and the rock). */
    private static void haze(VertexConsumer vc, Player p) {
        aim(p);
        Vec3 f = new Vec3(ax, ay, az), from = new Vec3(ox, oy, oz);
        Vec3 side = new Vec3(-az, 0, ax);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(f).normalize();
        for (int k = 0; k < 7; k++) {
            float[] ray = RAYS[k];
            double off = Math.toRadians(ray[1]);
            Vec3 dir = f.scale(Math.cos(off)).add(side.scale(Math.sin(off) * Math.cos(ray[0]))).add(up.scale(Math.sin(off) * Math.sin(ray[0])));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double d = hit.getLocation().distanceTo(from);
            float fall = (float) (1 - d / RANGE);
            pool(vc, hit.getLocation(), hit.getDirection(), (float) (0.6 + d * 0.18), 0.10F * fall * (k == 0 ? 1.4F : 1F));
        }
    }

    /** A soft disc of light lying on the face it hit: bright in the middle, nothing at its rim. */
    private static void pool(VertexConsumer vc, Vec3 at, Direction face, float r, float s) {
        float ux, uy, uz, vx, vy, vz;
        switch (face.getAxis()) {
            case Y -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1; }
            case X -> { ux = 0; uy = 1; uz = 0; vx = 0; vy = 0; vz = 1; }
            default -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0; }
        }
        float x = (float) (at.x + face.getStepX() * 0.02 - cx), y = (float) (at.y + face.getStepY() * 0.02 - cy), z = (float) (at.z + face.getStepZ() * 0.02 - cz);
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, 0F, r * 0.35F, s, s * 0.7F);
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, r * 0.35F, r, s * 0.7F, 0F);
    }

    private static final int SEG = 16;
    private static final float[] COS = new float[SEG + 1], SIN = new float[SEG + 1];

    static {
        for (int i = 0; i <= SEG; i++) {
            COS[i] = Mth.cos(i * Mth.TWO_PI / SEG);
            SIN[i] = Mth.sin(i * Mth.TWO_PI / SEG);
        }
    }

    private static void ring(VertexConsumer vc, float x, float y, float z, float ux, float uy, float uz, float vx, float vy, float vz,
                             float r0, float r1, float a0, float a1) {
        int c0 = col(a0), c1 = col(a1);
        for (int i = 0; i < SEG; i++) {
            float ca = COS[i], sa = SIN[i], cb = COS[i + 1], sb = SIN[i + 1];
            vc.addVertex(x + (ux * ca + vx * sa) * r0, y + (uy * ca + vy * sa) * r0, z + (uz * ca + vz * sa) * r0).setColor(c0);
            vc.addVertex(x + (ux * ca + vx * sa) * r1, y + (uy * ca + vy * sa) * r1, z + (uz * ca + vz * sa) * r1).setColor(c1);
            vc.addVertex(x + (ux * cb + vx * sb) * r1, y + (uy * cb + vy * sb) * r1, z + (uz * cb + vz * sb) * r1).setColor(c1);
            vc.addVertex(x + (ux * cb + vx * sb) * r0, y + (uy * cb + vy * sb) * r0, z + (uz * cb + vz * sb) * r0).setColor(c0);
        }
    }

    /** The lamp itself seen from outside: a hot little flame on the brow in a warm halo. */
    private static void lampHalo(VertexConsumer vc, Player p) {
        Vec3 eye = p.getEyePosition(pt), f = p.getViewVector(pt);
        Vec3 at = eye.add(0, 0.27, 0).add(f.scale(0.28));
        float x = (float) (at.x - cx), y = (float) (at.y - cy), z = (float) (at.z - cz);
        sprite(vc, x, y, z, 0.5F, col(0.5F), col(0F));
        sprite(vc, x, y, z, 0.09F, 0xF2FFF2DA, 0x00FFDCA0);
    }

    private static void sprite(VertexConsumer vc, float x, float y, float z, float r, int inner, int outer) {
        for (int i = 0; i < 8; i++) {
            int a = i * SEG / 8, b = (i + 1) * SEG / 8;
            float ax0 = (RIGHT.x * COS[a] + UP.x * SIN[a]) * r, ay0 = (RIGHT.y * COS[a] + UP.y * SIN[a]) * r, az0 = (RIGHT.z * COS[a] + UP.z * SIN[a]) * r;
            float bx = (RIGHT.x * COS[b] + UP.x * SIN[b]) * r, by = (RIGHT.y * COS[b] + UP.y * SIN[b]) * r, bz = (RIGHT.z * COS[b] + UP.z * SIN[b]) * r;
            vc.addVertex(x, y, z).setColor(inner);
            vc.addVertex(x + ax0, y + ay0, z + az0).setColor(outer);
            vc.addVertex(x + bx, y + by, z + bz).setColor(outer);
            vc.addVertex(x, y, z).setColor(inner);
        }
    }

    private static int col(float a) {
        // additive: the lightning blend takes alpha as the weight of the colour (and, lit, of the texture)
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (int) (R * 255F) << 16 | (int) (G * 255F) << 8 | (int) (B * 255F);
    }
}
