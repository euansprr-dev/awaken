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
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * The deep's light, drawn (no light blocks, nothing relit), in two additive passes shared by the miner's lamp and the
 * vein giving way: {@link #lit} redraws a block's own face quads (the model variant the world chose there, so it lines
 * up) over themselves, so the rock's own texture comes up out of the dark (an ore brightest where its ore pixels are),
 * and lays block-atlas sprites (the vein's cracks) on faces; {@link #glow} is soft untextured colour (a lamp's haze, a
 * pool of light on a face). If the lit pipeline doesn't compile that pass is skipped; the glow falls back to lightning.
 */
final class DelveRender {
    private DelveRender() {}

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
    private static int glowOk = -1;
    static int litOk = -1;

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

    static RenderType glow() {
        if (glowOk < 0) glowOk = compiled(GLOW) ? 1 : 0;
        if (glowOk == 0) return RenderTypes.lightning();
        if (glowType == null) glowType = RenderType.create("awaken_delve_lamp", RenderSetup.builder(GLOW).setOitPipelines(GLOW_OIT).createRenderSetup());
        return glowType;
    }

    /** The lit-rock pass, or null when its pipeline didn't compile. */
    static RenderType lit() {
        if (litOk < 0) litOk = compiled(LIT) ? 1 : 0;
        if (litOk == 0) return null;
        if (litType == null) litType = RenderType.create("awaken_delve_lamp_lit", RenderSetup.builder(LIT)
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS).createRenderSetup());
        return litType;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    /** The light at a vertex (world position): an additive colour, its alpha the strength. */
    interface VertexLight {
        int at(double x, double y, double z);
    }

    private static final List<BlockStateModelPart> PARTS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.createThreadLocalInstance(0L);
    private static final int[] C = new int[4];

    /**
     * One face of one block, drawn again over itself from its own model quads, lit by {@code light} (camera at cx, cy,
     * cz). Returns the quads drawn.
     */
    static int face(VertexConsumer vc, ClientLevel level, BlockState s, BlockPos pos, Direction face, VertexLight light,
                    double cx, double cy, double cz) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(s);
        PARTS.clear();
        RANDOM.setSeed(s.getSeed(pos));
        model.collectParts(level, pos, s, RANDOM, PARTS);
        int n = 0;
        float nx = face.getStepX() * 0.0015F, ny = face.getStepY() * 0.0015F, nz = face.getStepZ() * 0.0015F;
        for (BlockStateModelPart part : PARTS) {
            for (BakedQuad q : part.getQuads(face)) {
                boolean any = false;
                for (int v = 0; v < 4; v++) {
                    Vector3fc pv = q.position(v);
                    C[v] = light.at(pos.getX() + pv.x(), pos.getY() + pv.y(), pos.getZ() + pv.z());
                    any |= (C[v] >>> 24) > 1;
                }
                if (!any) continue;
                for (int v = 0; v < 4; v++) {
                    Vector3fc pv = q.position(v);
                    long uv = q.packedUV(v);
                    vc.addVertex((float) (pos.getX() + pv.x() + nx - cx), (float) (pos.getY() + pv.y() + ny - cy), (float) (pos.getZ() + pv.z() + nz - cz))
                            .setUv(UVPair.unpackU(uv), UVPair.unpackV(uv)).setColor(C[v]);
                }
                n++;
            }
        }
        return n;
    }

    /** A block-atlas sprite laid over a whole face of a block (its rotation picked by {@code turn}, 0-3). */
    static void sprite(VertexConsumer vc, TextureAtlasSprite sp, BlockPos pos, Direction face, int turn, int argb, double cx, double cy, double cz) {
        float o = 0.002F;
        float x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
        float[][] q = switch (face) {
            case UP -> new float[][]{{0, 1 + o, 0}, {0, 1 + o, 1}, {1, 1 + o, 1}, {1, 1 + o, 0}};
            case DOWN -> new float[][]{{0, -o, 0}, {1, -o, 0}, {1, -o, 1}, {0, -o, 1}};
            case NORTH -> new float[][]{{1, 1, -o}, {1, 0, -o}, {0, 0, -o}, {0, 1, -o}};
            case SOUTH -> new float[][]{{0, 1, 1 + o}, {0, 0, 1 + o}, {1, 0, 1 + o}, {1, 1, 1 + o}};
            case WEST -> new float[][]{{-o, 1, 0}, {-o, 0, 0}, {-o, 0, 1}, {-o, 1, 1}};
            case EAST -> new float[][]{{1 + o, 1, 1}, {1 + o, 0, 1}, {1 + o, 0, 0}, {1 + o, 1, 0}};
        };
        float[] us = {sp.getU0(), sp.getU0(), sp.getU1(), sp.getU1()}, vs = {sp.getV0(), sp.getV1(), sp.getV1(), sp.getV0()};
        for (int v = 0; v < 4; v++) {
            int k = (v + turn) & 3;
            vc.addVertex((float) (x0 + q[v][0] - cx), (float) (y0 + q[v][1] - cy), (float) (z0 + q[v][2] - cz)).setUv(us[k], vs[k]).setColor(argb);
        }
    }

    static TextureAtlasSprite blockSprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, id));
    }

    private static final int SEG = 16;
    private static final float[] COS = new float[SEG + 1], SIN = new float[SEG + 1];

    static {
        for (int i = 0; i <= SEG; i++) {
            COS[i] = Mth.cos(i * Mth.TWO_PI / SEG);
            SIN[i] = Mth.sin(i * Mth.TWO_PI / SEG);
        }
    }

    /** A soft disc of light lying on a face (camera-relative centre): {@code inner} in the middle, nothing at its rim. */
    static void pool(VertexConsumer vc, float x, float y, float z, Direction face, float r, int inner, int mid) {
        float ux, uy, uz, vx, vy, vz;
        switch (face.getAxis()) {
            case Y -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1; }
            case X -> { ux = 0; uy = 1; uz = 0; vx = 0; vy = 0; vz = 1; }
            default -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0; }
        }
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, 0F, r * 0.35F, inner, mid);
        ring(vc, x, y, z, ux, uy, uz, vx, vy, vz, r * 0.35F, r, mid, mid & 0xFFFFFF);
    }

    private static void ring(VertexConsumer vc, float x, float y, float z, float ux, float uy, float uz, float vx, float vy, float vz,
                             float r0, float r1, int c0, int c1) {
        for (int i = 0; i < SEG; i++) {
            float ca = COS[i], sa = SIN[i], cb = COS[i + 1], sb = SIN[i + 1];
            vc.addVertex(x + (ux * ca + vx * sa) * r0, y + (uy * ca + vy * sa) * r0, z + (uz * ca + vz * sa) * r0).setColor(c0);
            vc.addVertex(x + (ux * ca + vx * sa) * r1, y + (uy * ca + vy * sa) * r1, z + (uz * ca + vz * sa) * r1).setColor(c1);
            vc.addVertex(x + (ux * cb + vx * sb) * r1, y + (uy * cb + vy * sb) * r1, z + (uz * cb + vz * sb) * r1).setColor(c1);
            vc.addVertex(x + (ux * cb + vx * sb) * r0, y + (uy * cb + vy * sb) * r0, z + (uz * cb + vz * sb) * r0).setColor(c0);
        }
    }

    /** A soft disc facing the camera (right and up are the camera's): centre colour fading to nothing at its rim. */
    static void disc(VertexConsumer vc, float x, float y, float z, float r, int inner, int outer, Vector3f right, Vector3f up) {
        for (int i = 0; i < 8; i++) {
            int a = i * SEG / 8, b = (i + 1) * SEG / 8;
            float ax = (right.x * COS[a] + up.x * SIN[a]) * r, ay = (right.y * COS[a] + up.y * SIN[a]) * r, az = (right.z * COS[a] + up.z * SIN[a]) * r;
            float bx = (right.x * COS[b] + up.x * SIN[b]) * r, by = (right.y * COS[b] + up.y * SIN[b]) * r, bz = (right.z * COS[b] + up.z * SIN[b]) * r;
            vc.addVertex(x, y, z).setColor(inner);
            vc.addVertex(x + ax, y + ay, z + az).setColor(outer);
            vc.addVertex(x + bx, y + by, z + bz).setColor(outer);
            vc.addVertex(x, y, z).setColor(inner);
        }
    }

    /** Additive colour: rgb (0xRRGGBB) at strength {@code a} (the lightning blend weighs the colour by alpha). */
    static int col(int rgb, float a) {
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (rgb & 0xFFFFFF);
    }
}
