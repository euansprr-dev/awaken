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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The miner's lamp (PLAN 7.5): its light is drawn on the rock ahead of whoever wears it, the way the city's lanterns are
 * ({@code citizens.client.CityGlow}): a fan of rays from the brow finds the walls in front, and on each a soft pool of
 * warm light lies on the face it hit, brightest at the beam's middle and near, gone at its edge and at
 * {@link #RANGE} blocks. One additive pass (the game's lightning shaders, depth-tested, no depth written): nothing is a
 * light block, so nothing is relit. A lode's glint shows where the beam falls ({@link #lights}).
 */
public final class LampGlow {
    private LampGlow() {}

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline LIGHT = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/delve_lamp"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final OitPipelineSet LIGHT_OIT = OitPipelineSet.builder(Awaken.id("delve_lamp"),
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
        if (type == null) type = RenderType.create("awaken_delve_lamp", RenderSetup.builder(LIGHT).setOitPipelines(LIGHT_OIT).createRenderSetup());
        return type;
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------

    /** The beam's reach (blocks), its half-angle, and how far other wearers' lamps are drawn. */
    static final double RANGE = 22, OTHERS = 40;
    static final float CONE_DEG = 21F;
    /** Rays: one down the middle, a ring of six at a third of the cone, a ring of eight at its edge. */
    private static final float[][] RAYS = rays();
    /** Warm lamp light (a carbide flame through brass): the pool's colour. */
    private static final float R = 1.00F, G = 0.80F, B = 0.52F;

    private static float[][] rays() {
        float[][] out = new float[15][];
        out[0] = new float[]{0, 0, 1F};
        for (int i = 0; i < 6; i++) out[1 + i] = new float[]{(float) (i * Math.PI / 3), CONE_DEG * 0.42F, 0.8F};
        for (int i = 0; i < 8; i++) out[7 + i] = new float[]{(float) (i * Math.PI / 4 + 0.2), CONE_DEG * 0.85F, 0.5F};
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
        return d < RANGE && d > 0.5 && p.getViewVector(1F).dot(to.scale(1 / d)) > Math.cos(Math.toRadians(CONE_DEG + 4));
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    private static double cx, cy, cz;
    private static float pt;
    private static ClientLevel level;

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
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light(), DRAW);
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, vc) -> {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (level == null || mc.player == null) return;
            for (Player p : level.players()) {
                if (!wears(p) || p.distanceToSqr(mc.player) > OTHERS * OTHERS) continue;
                beam(vc, p);
                if (p != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson()) lampHalo(vc, p);
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] lamp light failed", ex);
        }
    };

    private static void beam(VertexConsumer vc, Player p) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 f = p.getViewVector(pt);
        Vec3 side = new Vec3(-f.z, 0, f.x);
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(f).normalize();
        // the lamp sits on the brow, a hand above the eye
        Vec3 from = eye.add(up.scale(0.22)).add(f.scale(0.2));
        for (float[] ray : RAYS) {
            double off = Math.toRadians(ray[1]);
            Vec3 dir = f.scale(Math.cos(off)).add(side.scale(Math.sin(off) * Math.cos(ray[0]))).add(up.scale(Math.sin(off) * Math.sin(ray[0])));
            Vec3 end = from.add(dir.scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(from, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double d = hit.getLocation().distanceTo(from);
            float fall = (float) (1 - d / RANGE);
            float strength = 0.30F * ray[2] * fall * fall;
            if (strength < 0.01F) continue;
            float radius = (float) (0.45 + d * (ray[0] == 0 && ray[1] == 0 ? 0.13 : 0.17));
            pool(vc, hit.getLocation(), hit.getDirection(), radius, strength);
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
        float x = (float) (at.x + face.getStepX() * 0.015 - cx), y = (float) (at.y + face.getStepY() * 0.015 - cy), z = (float) (at.z + face.getStepZ() * 0.015 - cz);
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, 0F, r * 0.3F, s, s * 0.85F);
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, r * 0.3F, r * 0.65F, s * 0.85F, s * 0.4F);
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, r * 0.65F, r, s * 0.4F, 0F);
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
            float ax = (RIGHT.x * COS[a] + UP.x * SIN[a]) * r, ay = (RIGHT.y * COS[a] + UP.y * SIN[a]) * r, az = (RIGHT.z * COS[a] + UP.z * SIN[a]) * r;
            float bx = (RIGHT.x * COS[b] + UP.x * SIN[b]) * r, by = (RIGHT.y * COS[b] + UP.y * SIN[b]) * r, bz = (RIGHT.z * COS[b] + UP.z * SIN[b]) * r;
            vc.addVertex(x, y, z).setColor(inner);
            vc.addVertex(x + ax, y + ay, z + az).setColor(outer);
            vc.addVertex(x + bx, y + by, z + bz).setColor(outer);
            vc.addVertex(x, y, z).setColor(inner);
        }
    }

    private static int col(float a) {
        // additive: pre-scaled by strength (the lightning blend takes alpha as the source weight)
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (int) (R * 255F) << 16 | (int) (G * 255F) << 8 | (int) (B * 255F);
    }
}
