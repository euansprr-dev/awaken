package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.lens.client.LensGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Power effects over Lens (SPEC §36.12 "Effects over Lens"; §22 "Light over the Air").
 *
 * <p>The effects' render types are the game's own (lightning, energy swirl, translucent entity), and three of those
 * write depth. Lens works from the world's depth: a glow's whole quad, clear corners and all, was a surface to it. The
 * water behind a cast lost Lens's water and showed the game's flat teal in the quad's shape; the fog, the shade and
 * the sky behind were worked out for the effect's distance and not the world's (Euan, playing, 12 Nov: "when I use
 * powers and there's water behind it, the powers and the water clash weirdly and create this weird like glitch effect
 * ... it's not just with the water, it's with the entire shader pack"). Leaving the depth writes out is not enough
 * by itself: light that is only in the frame is then part of whatever lies behind it, and Lens paints its own sky and
 * fog over that.
 *
 * <p>So while Lens will lay its light layer over the picture ({@link LensGlow#laying}), the five batches of
 * {@link FxRender} are not handed to the game's renderer but drawn here, after the level, into that layer: the same
 * vertices, the same shaders and blends as the render types they stand in for, tested against the world's depth and
 * writing none. Lens sees the world alone and treats it as it should; the effects go on top of its finished picture,
 * unshaded and unfogged (they are light), and its bloom takes them from the layer. With no Lens chain to lay the layer
 * (Lens off, Swift, a scene's tear) the effects go the game's own way as before.
 *
 * <p>A layer's alpha is how much of the picture behind is covered: additive light covers none (its blend leaves alpha
 * alone), smoke and ink cover by their own alpha. The alpha-blended batches are sorted far to near, as the game sorts
 * a translucent render type. Nothing is allocated per frame.
 */
final class FxGlow {
    private FxGlow() {}

    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    // the game's lightning and additive blends for colour, the layer's alpha left as it is
    private static final BlendFunction LIGHT = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE);
    private static final BlendFunction ADDED = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE);

    /** As {@code RenderPipelines.LIGHTNING}: rings, spheres, beams, trails, bolts, waves, seams. */
    static final RenderPipeline MESHES = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Awaken.id("pipeline/fx_glow_meshes"))
            .withColorTargetState(new ColorTargetState(LIGHT))
            .withDepthStencilState(TEST_ONLY)
            .build();
    /** As {@code RenderPipelines.ENERGY_SWIRL}: additive sprites of the particle atlas. */
    static final RenderPipeline ADD_SPRITES = RenderPipeline.builder(RenderPipelines.ENERGY_SWIRL_SNIPPET)
            .withLocation(Awaken.id("pipeline/fx_glow_add_sprites"))
            .withColorTargetState(new ColorTargetState(ADDED))
            .withDepthStencilState(TEST_ONLY)
            .build();
    /** As {@code RenderPipelines.ENTITY_TRANSLUCENT}: smoke, dust and decals, lit by the world's light. */
    static final RenderPipeline LIT_SPRITES = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Awaken.id("pipeline/fx_glow_lit_sprites"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(TEST_ONLY)
            .build();
    /** As {@code RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE}: the daylight underlay, ink and dark cores. */
    static final RenderPipeline BRIGHT_SPRITES = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Awaken.id("pipeline/fx_glow_bright_sprites"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(TEST_ONLY)
            .build();

    static void register(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(MESHES);
        e.registerOptionalPipeline(ADD_SPRITES);
        e.registerOptionalPipeline(LIT_SPRITES);
        e.registerOptionalPipeline(BRIGHT_SPRITES);
    }

    // the batches, in draw order: daylight underlays, lit smoke and dust, additive light (meshes, then sprites), dark ink
    private static final int UNDERLAY = 0, LIT = 1, MESH = 2, ADD = 3, BRIGHT = 4, BATCHES = 5;
    private static final int POSITION_COLOR = 16, ENTITY = 36;
    private static final int[] STRIDE = {ENTITY, ENTITY, POSITION_COLOR, ENTITY, ENTITY};
    // quads a batch can hold: every shape's mesh at full budget and every sprite of the pool with room to spare; what
    // does not fit is not drawn that frame
    private static final int[] QUADS = {12288, 4096, 12288, 6144, 4096};
    private static final boolean[] SORTED = {false, true, false, false, true};
    private static final MappableRingBuffer[] RINGS = new MappableRingBuffer[BATCHES];
    private static final Sink SINK = new Sink();
    private static final int[] drawn = new int[BATCHES];
    private static final boolean[] wanted = new boolean[BATCHES];
    private static boolean any, off;
    // far-to-near order for the alpha-blended batches: one index buffer both use in turn
    private static final int SORT_QUADS = 4096;
    private static @Nullable MappableRingBuffer sortRing;
    private static final long[] keys = new long[SORT_QUADS];
    private static final int[] firstIndex = new int[BATCHES];

    /**
     * Whether this frame's effects are drawn here and not by the game's renderer: Lens is about to lay its light layer
     * over the picture, the pipelines compiled, and nothing has failed this session.
     */
    static boolean takes() {
        return !off && !forcedOff && LensGlow.laying() && RenderSystem.getCompiledPipelineNullable(MESHES) != null
                && RenderSystem.getCompiledPipelineNullable(ADD_SPRITES) != null && RenderSystem.getCompiledPipelineNullable(LIT_SPRITES) != null
                && RenderSystem.getCompiledPipelineNullable(BRIGHT_SPRITES) != null;
    }

    /** FxRender, at submit time, in place of handing the batches to the game: which of them this frame has. */
    static void want(boolean underlay, boolean lit, boolean meshes, boolean add, boolean bright) {
        wanted[UNDERLAY] = underlay;
        wanted[LIT] = lit;
        wanted[MESH] = meshes;
        wanted[ADD] = add;
        wanted[BRIGHT] = bright;
        any = underlay || lit || meshes || add || bright;
    }

    /** After the level, before Lens settles its layer: the batches asked for at submit time, drawn into it. */
    static void afterLevel(RenderLevelStageEvent.AfterLevel e) {
        if (!any) return;
        any = false;
        try {
            draw(e);
        } catch (RuntimeException ex) {
            off = true;
            Awaken.LOG.error("Power FX over Lens failed; effects are drawn the game's own way for this session", ex);
        }
    }

    private static void draw(RenderLevelStageEvent.AfterLevel e) {
        Minecraft mc = Minecraft.getInstance();
        int total = 0;
        for (int b = 0; b < BATCHES; b++) {
            drawn[b] = 0;
            if (!wanted[b]) continue;
            if (RINGS[b] == null) {
                final int which = b;
                RINGS[b] = new MappableRingBuffer(() -> "awaken power fx " + which, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, QUADS[b] * 4 * STRIDE[b]);
            }
            try (GpuBufferSlice.MappedView mapped = RINGS[b].currentBuffer().map(false, true)) {
                long start = MemoryUtil.memAddress(mapped.data());
                SINK.begin(start, start + (long) QUADS[b] * 4 * STRIDE[b], STRIDE[b] == ENTITY);
                renderer(b).render(null, SINK);
                drawn[b] = SINK.vertices() / 4;
                if (SORTED[b] && drawn[b] > 1) sortKeys(b, start);
            }
            total += drawn[b];
        }
        if (total > 0) {
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            int most = 0;
            for (int b = 0; b < BATCHES; b++) most = Math.max(most, drawn[b]);
            GpuBuffer indices = quadIndex.getBuffer(most * 6);
            GpuBuffer sorted = sortedIndices();
            AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            GpuTextureView overlay = mc.gameRenderer.overlayTexture().getTextureView(), lightmap = mc.gameRenderer.lightmap();
            var clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            try (RenderPass pass = LensGlow.open(() -> "awaken power fx", main)) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(e.getModelViewMatrix())));
                for (int b = 0; b < BATCHES; b++) {
                    if (drawn[b] == 0) continue;
                    pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(b)));
                    if (b != MESH) {
                        pass.setUniform("Sampler0", atlas.getTextureView(), atlas.getSampler());
                        if (b != ADD) pass.setUniform("Sampler1", overlay, clamp);
                        if (b == LIT) pass.setUniform("Sampler2", lightmap, clamp);
                    }
                    pass.setVertexBuffer(0, RINGS[b].currentBuffer().slice());
                    if (SORTED[b] && sorted != null && drawn[b] > 1) {
                        pass.setIndexBuffer(sorted, IndexType.INT);
                        pass.drawIndexed(drawn[b] * 6, 1, firstIndex[b], 0, 0);
                    } else {
                        pass.setIndexBuffer(indices, quadIndex.type());
                        pass.drawIndexed(drawn[b] * 6, 1, 0, 0, 0);
                    }
                }
            }
        }
        for (int b = 0; b < BATCHES; b++) if (wanted[b] && RINGS[b] != null) RINGS[b].rotate();
        if (sortRing != null) sortRing.rotate();
    }

    private static SubmitNodeCollector.CustomGeometryRenderer renderer(int batch) {
        return switch (batch) {
            case UNDERLAY -> FxRender.UNDERLAY;
            case LIT -> FxRender.LIT_SPRITES;
            case MESH -> FxRender.MESHES;
            case ADD -> FxRender.ADD_SPRITES;
            default -> FxRender.BRIGHT_SPRITES;
        };
    }

    private static RenderPipeline pipeline(int batch) {
        return switch (batch) {
            case LIT -> LIT_SPRITES;
            case MESH -> MESHES;
            case ADD -> ADD_SPRITES;
            default -> BRIGHT_SPRITES;
        };
    }

    // ---- far to near ------------------------------------------------------------------------------------------------

    private static int sortedCount;
    private static final int[][] order = new int[BATCHES][];

    /** The batch's quads by distance from the camera (the vertices are camera-relative), farthest first. */
    private static void sortKeys(int batch, long start) {
        int n = Math.min(drawn[batch], SORT_QUADS);
        drawn[batch] = n;
        if (order[batch] == null) order[batch] = new int[SORT_QUADS];
        for (int q = 0; q < n; q++) {
            long v = start + (long) q * 4 * ENTITY;
            float x = 0f, y = 0f, z = 0f;
            for (int k = 0; k < 4; k++) {
                x += MemoryUtil.memGetFloat(v + (long) k * ENTITY);
                y += MemoryUtil.memGetFloat(v + (long) k * ENTITY + 4);
                z += MemoryUtil.memGetFloat(v + (long) k * ENTITY + 8);
            }
            // (a positive float's bits order as the float does; the far end first, so the complement)
            keys[q] = ((long) ~Float.floatToRawIntBits(x * x + y * y + z * z) & 0xFFFFFFFFL) << 32 | q;
        }
        java.util.Arrays.sort(keys, 0, n);
        int[] o = order[batch];
        for (int q = 0; q < n; q++) o[q] = (int) keys[q];
    }

    /** One index buffer holding each sorted batch's quads in its order, one after the other; null when none is sorted. */
    private static @Nullable GpuBuffer sortedIndices() {
        int quads = 0;
        for (int b = 0; b < BATCHES; b++) if (SORTED[b] && drawn[b] > 1) quads += drawn[b];
        if (quads == 0) return null;
        if (sortRing == null) {
            sortRing = new MappableRingBuffer(() -> "awaken power fx order", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_MAP_WRITE, 2 * SORT_QUADS * 6 * 4);
        }
        GpuBuffer buffer = sortRing.currentBuffer();
        try (GpuBufferSlice.MappedView mapped = buffer.map(false, true)) {
            long at = MemoryUtil.memAddress(mapped.data());
            int first = 0;
            for (int b = 0; b < BATCHES; b++) {
                if (!SORTED[b] || drawn[b] <= 1) continue;
                firstIndex[b] = first;
                int[] o = order[b];
                for (int q = 0; q < drawn[b]; q++) {
                    int v = o[q] * 4;
                    // the game's own order for a quad's two triangles
                    MemoryUtil.memPutInt(at, v);
                    MemoryUtil.memPutInt(at + 4, v + 1);
                    MemoryUtil.memPutInt(at + 8, v + 2);
                    MemoryUtil.memPutInt(at + 12, v + 2);
                    MemoryUtil.memPutInt(at + 16, v + 3);
                    MemoryUtil.memPutInt(at + 20, v);
                    at += 24;
                }
                first += drawn[b] * 6;
            }
        }
        return buffer;
    }

    // ---- the vertices -----------------------------------------------------------------------------------------------

    /**
     * Writes the vertices FxRender builds straight into a mapped buffer, in the layout of the pipeline that will draw
     * them: position and colour for the meshes; position, colour, texture, overlay, light and normal (the game's entity
     * vertex) for the rest. A full buffer takes no more: the rest of that batch is left out.
     */
    private static final class Sink implements VertexConsumer {
        private long at, end, vertex;
        private int stride, count;
        private boolean entity;

        void begin(long start, long end, boolean entity) {
            at = start;
            this.end = end;
            this.entity = entity;
            stride = entity ? ENTITY : POSITION_COLOR;
            count = 0;
            vertex = 0L;
        }

        int vertices() {
            return count;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (at + stride > end) {
                vertex = 0L;
                return this;
            }
            vertex = at;
            at += stride;
            count++;
            MemoryUtil.memPutFloat(vertex, x);
            MemoryUtil.memPutFloat(vertex + 4, y);
            MemoryUtil.memPutFloat(vertex + 8, z);
            MemoryUtil.memPutInt(vertex + 12, -1);
            if (entity) {
                MemoryUtil.memPutLong(vertex + 16, 0L);
                setOverlay(OverlayTexture.NO_OVERLAY);
                setLight(LightCoordsUtil.FULL_BRIGHT);
                MemoryUtil.memPutInt(vertex + 32, 0x00007F00); // normal 0, 1, 0
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (vertex != 0L) MemoryUtil.memPutInt(vertex + 12, (a & 255) << 24 | (b & 255) << 16 | (g & 255) << 8 | r & 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (vertex != 0L && entity) {
                MemoryUtil.memPutFloat(vertex + 16, u);
                MemoryUtil.memPutFloat(vertex + 20, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (vertex != 0L && entity) {
                MemoryUtil.memPutShort(vertex + 24, (short) u);
                MemoryUtil.memPutShort(vertex + 26, (short) v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (vertex != 0L && entity) {
                MemoryUtil.memPutShort(vertex + 28, (short) u);
                MemoryUtil.memPutShort(vertex + 30, (short) v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (vertex != 0L && entity) {
                MemoryUtil.memPutByte(vertex + 32, (byte) (int) (Math.clamp(x, -1f, 1f) * 127f));
                MemoryUtil.memPutByte(vertex + 33, (byte) (int) (Math.clamp(y, -1f, 1f) * 127f));
                MemoryUtil.memPutByte(vertex + 34, (byte) (int) (Math.clamp(z, -1f, 1f) * 127f));
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    private static boolean forcedOff;

    /** {@code /awaken fx glow off|on|setting}: the A/B switch, and which way the effects go now. */
    static String force(String arg) {
        forcedOff = arg.trim().equals("off");
        return "power effects: " + (off ? "the game's own way (drawing over Lens failed this session)"
                : forcedOff ? "the game's own way, into the frame Lens reads (as before)"
                : takes() ? "over Lens, in its light layer" : "over Lens when it lays its light layer (not now: the game's own way)");
    }
}
