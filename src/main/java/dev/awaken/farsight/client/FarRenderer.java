package dev.awaken.farsight.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.farsight.FarColumns;
import dev.awaken.farsight.FarLandmarks;
import dev.awaken.farsight.FarTrees;
import dev.awaken.farsight.Rings;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the rings (SPEC §20) right after the sky, before any chunk: real chunks then simply draw over far terrain, so
 * the two never fight over depth. Every ring is the same grid of corners; its window, minus the window of the ring
 * inside it, is cut into pieces of at most 64×64 cells, and a piece is drawn only if it is in view and not entirely
 * under the real chunks. Far Sight 2 draws each piece as one continuous mesh: vertex {@code v} is the piece's corner
 * {@code (v % 65, v / 65)}, worked out in the vertex shader from the atlases, and the triangles come from one index
 * buffer per piece width (every width is a multiple of 8 cells: windows move by whole patches), whose first {@code h}
 * rows make a piece {@code h} cells tall.
 *
 * <p>Far Sight keeps its own depth (reversed-Z, the device's range, near {@link #NEAR}, far {@link #FAR}) in a texture
 * of the main target's size. Nothing else writes it, so it lasts until post effects, where Lens reads it through
 * compat.client.FarTerrain to haze far terrain by its real distance.
 */
final class FarRenderer implements AutoCloseable {
    static final float NEAR = 1f, FAR = 32768f;
    /** The far air's scale height (blocks) and how strongly it scatters sunlight toward the eye. */
    static final float AIR_SCALE_HEIGHT = 500f, AIR_SUN = 0.9f;
    static final int PIECE = 64, MAX_DRAWS = 256, STRIDE = PIECE + 1, WIDTHS = PIECE / 8;
    private static final int INDICES_PER_CELL = 6;
    static final int FRAME_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get()
            + Rings.RINGS * 16 * 2 + 4 * 16;

    private final MappableRingBuffer frameUbo = new MappableRingBuffer(() -> "awaken far sight frame", 130, FRAME_SIZE);
    private final int align = Math.max(16, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());
    private final MappableRingBuffer drawUbo = new MappableRingBuffer(() -> "awaken far sight draws", 130, MAX_DRAWS * align);
    static final int MAX_MARKS = 48;
    private final MappableRingBuffer markUbo = new MappableRingBuffer(() -> "awaken far sight landmarks", 130, MAX_MARKS * 3 * 16);
    /** Index buffers by piece width (8, 16, .. 64 cells), each 64 rows tall; also the landmarks' quads. */
    private final GpuBuffer[] widths = new GpuBuffer[WIDTHS];
    private final GpuBuffer quads;
    /** Far forests: one tree run's triangles (5 quads a tree), each run's patch origin, and the frame's draws. */
    static final int TREE_VERTS = 20, TREE_INDICES = 30, COMMAND_BYTES = 20;
    private final GpuBuffer treeIndices;
    private final MappableRingBuffer treeSlots = new MappableRingBuffer(() -> "awaken far sight tree slots",
            GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, FarField.TREE_RINGS * Rings.PATCHES * Rings.PATCHES * 16);
    private final MappableRingBuffer treeCommands = new MappableRingBuffer(() -> "awaken far sight tree draws",
            GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_INDIRECT_PARAMETERS, FarField.TREE_RINGS * Rings.PATCHES * Rings.PATCHES * 2 * COMMAND_BYTES);
    /** This frame's tree draws: index count and first vertex per run in view. */
    private final int[] treeCount = new int[FarField.TREE_RINGS * Rings.PATCHES * Rings.PATCHES * 2], treeVertex = new int[treeCount.length];
    int treeDraws, treesDrawn;
    /** Multi-draw indirect where the backend has it (Vulkan); a draw per run elsewhere (OpenGL 4.1 has none). */
    private final boolean indirect = "Vulkan".equalsIgnoreCase(RenderSystem.getDevice().getDeviceInfo().backendName());
    private GpuTexture depth;
    private GpuTextureView depthView;
    /**
     * Which real chunks the client has (R8, 64×64, by chunk coordinate masked to 63): far terrain stays hidden under
     * the real chunks only where they are there, so a teleport or a fast flight never opens a hole to the sky.
     */
    private final GpuTexture chunks;
    private final GpuTextureView chunksView;
    private final ByteBuffer chunkBytes = MemoryUtil.memCalloc(64 * 64);

    // the frame's pieces: ring, first cell (window-local), width and height in cells
    private final int[] pRing = new int[MAX_DRAWS], pX = new int[MAX_DRAWS], pZ = new int[MAX_DRAWS], pW = new int[MAX_DRAWS], pH = new int[MAX_DRAWS];
    int pieces, cells;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f cull = new Matrix4f();
    private final float[] originX = new float[Rings.RINGS], originZ = new float[Rings.RINGS];
    // GPU time of Far Sight's passes: timestamps before and after, read back three frames later (diagnostics only)
    private final GpuQueryPool[] timers = new GpuQueryPool[3];
    private int timerFrame;
    private boolean timing = true;
    private double gpuNanos, bakeNanos;
    private int gpuFrames;

    FarRenderer() {
        ByteBuffer data = MemoryUtil.memAlloc(PIECE * PIECE * INDICES_PER_CELL * 2).order(ByteOrder.nativeOrder());
        for (int k = 0; k < WIDTHS; k++) {
            int w = (k + 1) * 8;
            data.clear();
            for (int j = 0; j < PIECE; j++) {
                for (int i = 0; i < w; i++) {
                    int a = j * STRIDE + i, b = a + 1, c = a + STRIDE, d = c + 1;
                    // alternate the diagonal, so long slopes don't all lean one way
                    if (((i ^ j) & 1) == 0) data.putShort((short) a).putShort((short) c).putShort((short) d).putShort((short) a).putShort((short) d).putShort((short) b);
                    else data.putShort((short) a).putShort((short) c).putShort((short) b).putShort((short) b).putShort((short) c).putShort((short) d);
                }
            }
            data.flip();
            widths[k] = RenderSystem.getDevice().createBuffer(() -> "awaken far sight mesh " + w, GpuBuffer.USAGE_INDEX, data);
        }
        // the landmarks' quads: vertices 4q..4q+3 as two triangles
        data.clear();
        for (int q = 0; q < MAX_MARKS * 2; q++) {
            int v = q * 4;
            data.putShort((short) v).putShort((short) (v + 1)).putShort((short) (v + 2)).putShort((short) (v + 2)).putShort((short) (v + 3)).putShort((short) v);
        }
        data.flip();
        quads = RenderSystem.getDevice().createBuffer(() -> "awaken far sight quads", GpuBuffer.USAGE_INDEX, data);
        MemoryUtil.memFree(data);
        ByteBuffer ti = MemoryUtil.memAlloc(FarTrees.MAX * TREE_INDICES * 2).order(ByteOrder.nativeOrder());
        for (int t = 0; t < FarTrees.MAX; t++) {
            for (int q = 0; q < 5; q++) {
                int v = t * TREE_VERTS + q * 4;
                ti.putShort((short) v).putShort((short) (v + 1)).putShort((short) (v + 2)).putShort((short) (v + 2)).putShort((short) (v + 3)).putShort((short) v);
            }
        }
        ti.flip();
        treeIndices = RenderSystem.getDevice().createBuffer(() -> "awaken far sight tree triangles", GpuBuffer.USAGE_INDEX, ti);
        MemoryUtil.memFree(ti);
        chunks = RenderSystem.getDevice().createTexture(() -> "awaken far sight chunks", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.R8_UNORM, 64, 64, 1, 1);
        chunksView = RenderSystem.getDevice().createTextureView(chunks);
    }

    /** Marks which chunks within {@code radius} of chunk (cx, cz) the client has. A 64×64 sweep at most: cheap. */
    void chunks(net.minecraft.world.level.chunk.ChunkSource source, int cx, int cz, int radius) {
        radius = Math.min(radius, 31);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx, z = cz + dz;
                chunkBytes.put(((z & 63) << 6) | (x & 63), source.hasChunk(x, z) ? (byte) 255 : 0);
            }
        }
        chunkBytes.clear();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(chunks, chunkBytes, 0, 0, 0, 0, 64, 64);
    }

    /** Far Sight's depth texture at the main target's size, recreated when the window is. */
    GpuTexture depth(RenderTarget main) {
        if (depth == null || depth.getWidth(0) != main.width || depth.getHeight(0) != main.height) {
            if (depthView != null) depthView.close();
            if (depth != null) depth.close();
            depth = RenderSystem.getDevice().createTexture(() -> "awaken far sight depth", 15, GpuFormat.D32_FLOAT, main.width, main.height, 1, 1);
            depthView = RenderSystem.getDevice().createTextureView(depth);
        }
        return depth;
    }

    /**
     * Decides this frame's pieces: each ring's window minus the one inside it, in view, and not wholly inside
     * {@code hiddenWithin} (horizontal blocks from the camera, where the real chunks cover everything).
     */
    void plan(FarField field, double camX, double camY, double camZ, Matrix4fc viewRotation, float fovDegrees, float aspect,
            float hiddenWithin, int minY, int maxY) {
        pieces = 0;
        cells = 0;
        cull.setPerspective(fovDegrees * (float) (Math.PI / 180.0), aspect, 0.5f, FAR).mul(viewRotation);
        frustum.set(cull, false);
        for (int r = 0; r < Rings.RINGS; r++) {
            if (!field.valid[r]) continue;
            int s = Rings.cellSize(r);
            originX[r] = (float) ((double) field.winX[r] * Rings.patchBlocks(r) - camX);
            originZ[r] = (float) ((double) field.winZ[r] * Rings.patchBlocks(r) - camZ);
            // the hole: the window of the ring inside, in this ring's cells (its corners fall on whole cells)
            int hx0 = 0, hz0 = 0, hx1 = 0, hz1 = 0;
            if (r > 0 && field.valid[r - 1]) {
                hx0 = field.winX[r - 1] * Rings.PATCH / 2 - field.winX[r] * Rings.PATCH;
                hz0 = field.winZ[r - 1] * Rings.PATCH / 2 - field.winZ[r] * Rings.PATCH;
                hx1 = hx0 + Rings.SIZE / 2;
                hz1 = hz0 + Rings.SIZE / 2;
            }
            if (hx1 > hx0) {
                rect(field, r, s, 0, 0, Rings.SIZE, hz0, camY, hiddenWithin, minY, maxY);
                rect(field, r, s, 0, hz1, Rings.SIZE, Rings.SIZE, camY, hiddenWithin, minY, maxY);
                rect(field, r, s, 0, hz0, hx0, hz1, camY, hiddenWithin, minY, maxY);
                rect(field, r, s, hx1, hz0, Rings.SIZE, hz1, camY, hiddenWithin, minY, maxY);
            } else {
                rect(field, r, s, 0, 0, Rings.SIZE, Rings.SIZE, camY, hiddenWithin, minY, maxY);
            }
        }
    }

    /**
     * Decides this frame's tree runs: for rings 1-4, every patch in view, not wholly under the real chunks and not wholly
     * inside the finer ring's window (its own trees stand there), draws its predicted run and its real chunks' run.
     */
    void planTrees(FarField field, double camY, float hiddenWithin) {
        treeDraws = 0;
        treesDrawn = 0;
        for (int r = FarTrees.FIRST_RING; r <= FarTrees.LAST_RING; r++) {
            if (!field.valid[r]) continue;
            int pb = Rings.patchBlocks(r);
            // the finer ring's window, blocks from the camera
            float ix0 = Float.MAX_VALUE, iz0 = Float.MAX_VALUE, ix1 = -Float.MAX_VALUE, iz1 = -Float.MAX_VALUE;
            if (field.valid[r - 1]) {
                ix0 = originX[r - 1];
                iz0 = originZ[r - 1];
                ix1 = ix0 + Rings.SIZE * Rings.cellSize(r - 1);
                iz1 = iz0 + Rings.SIZE * Rings.cellSize(r - 1);
            }
            for (int j = 0; j < Rings.PATCHES; j++) {
                for (int i = 0; i < Rings.PATCHES; i++) {
                    int px = field.winX[r] + i, pz = field.winZ[r] + j, slot = Rings.slot(px, pz);
                    int pred = field.treeCount[r][slot * 2], gen = field.treeCount[r][slot * 2 + 1];
                    if (pred == 0 && gen == 0) continue;
                    float ax = originX[r] + i * pb, az = originZ[r] + j * pb, bx = ax + pb, bz = az + pb;
                    if (ax >= ix0 && az >= iz0 && bx <= ix1 && bz <= iz1) continue;
                    float fx = Math.max(Math.abs(ax), Math.abs(bx)), fz = Math.max(Math.abs(az), Math.abs(bz));
                    if (fx * fx + fz * fz < hiddenWithin * hiddenWithin) continue;
                    int lo = (field.lo[r][slot] & 0xFFFF) - FarColumns.HEIGHT_BASE - 2, hi = field.top(r, slot) - FarColumns.HEIGHT_BASE + 40;
                    if (!frustum.testAab(ax, (float) (lo - camY), az, bx, (float) (hi - camY), bz)) continue;
                    for (int layer = 0; layer < 2; layer++) {
                        int n = layer == 0 ? pred : gen;
                        if (n == 0) continue;
                        treeCount[treeDraws] = n * TREE_INDICES;
                        treeVertex[treeDraws] = FarField.treeRun(r, slot, layer == 1) * FarTrees.MAX * TREE_VERTS;
                        treeDraws++;
                        treesDrawn += n;
                    }
                }
            }
        }
    }

    private void rect(FarField field, int r, int s, int x0, int z0, int x1, int z1, double camY, float hiddenWithin, int minY, int maxY) {
        for (int z = z0; z < z1; z += PIECE) {
            for (int x = x0; x < x1; x += PIECE) {
                int w = Math.min(PIECE, x1 - x), h = Math.min(PIECE, z1 - z);
                if (w <= 0 || h <= 0 || pieces >= MAX_DRAWS) continue;
                float ax = originX[r] + x * s, az = originZ[r] + z * s, bx = ax + w * s, bz = az + h * s;
                // wholly under the real chunks: its farthest corner is nearer than where far terrain starts
                float fx = Math.max(Math.abs(ax), Math.abs(bx)), fz = Math.max(Math.abs(az), Math.abs(bz));
                if (fx * fx + fz * fz < hiddenWithin * hiddenWithin) continue;
                int lo = minY, hi = maxY;
                long range = range(field, r, x, z, w, h);
                if (range != Long.MIN_VALUE) {
                    lo = (int) (range >> 32);
                    hi = (int) range;
                }
                if (!frustum.testAab(ax, (float) (lo - camY), az, bx, (float) (hi - camY), bz)) continue;
                pRing[pieces] = r;
                pX[pieces] = x;
                pZ[pieces] = z;
                pW[pieces] = w;
                pH[pieces] = h;
                pieces++;
                cells += w * h;
            }
        }
    }

    /** Lowest and highest top over the piece's patches ({@code lo << 32 | hi}), or MIN_VALUE if any is missing. */
    private static long range(FarField field, int r, int x, int z, int w, int h) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int pz = z / Rings.PATCH; pz <= Math.min(Rings.PATCHES - 1, (z + h) / Rings.PATCH); pz++) {
            for (int px = x / Rings.PATCH; px <= Math.min(Rings.PATCHES - 1, (x + w) / Rings.PATCH); px++) {
                int wx = field.winX[r] + px, wz = field.winZ[r] + pz;
                if (!field.has(r, wx, wz)) return Long.MIN_VALUE;
                int slot = Rings.slot(wx, wz);
                lo = Math.min(lo, (field.lo[r][slot] & 0xFFFF) - FarColumns.HEIGHT_BASE);
                hi = Math.max(hi, field.top(r, slot) - FarColumns.HEIGHT_BASE);
            }
        }
        // the outer band eases into the coarser ring, whose surface between its corners can sit a little apart
        return (long) (lo - 32) << 32 | (hi + 32) & 0xFFFFFFFFL;
    }

    /**
     * Draws the planned pieces into the main colour target and Far Sight's depth. {@code sun} is the light's direction
     * (world space); {@code fogStart}/{@code fogEnd} Far Sight's own fog at the horizon; {@code clipStart}/{@code clipBand}
     * where it dithers in under the real chunks.
     */
    void draw(FarField field, RenderTarget main, Matrix4fc viewRotation, double camX, double camY, double camZ, Vector3f sun,
            float sunStrength, Vector3f sunColour, float golden, float haze, float rain, float mistFloor, float mistDepth, float mistDensity,
            float mistCover, float fogStart, float fogEnd, float clipStart,
            float clipBand, int seaLevel, boolean zeroToOne, GpuTextureView lightmap, List<FarLandmarks.Landmark> landmarks) {
        depth(main);
        // clip.z = A + B * clip.w: Far Sight's own planes on the game's x, y and w (so view bobbing and FOV still apply)
        float a, b;
        if (zeroToOne) {
            a = NEAR * FAR / (FAR - NEAR);
            b = -NEAR / (FAR - NEAR);
        } else {
            a = 2f * NEAR * FAR / (FAR - NEAR);
            b = -(NEAR + FAR) / (FAR - NEAR);
        }
        GpuBuffer frame = frameUbo.currentBuffer();
        try (var view = frame.map(false, true)) {
            Std140Builder u = Std140Builder.intoBuffer(view.data());
            // the camera as an exact world cell (a multiple of 256) and the rest, so the shaders' world patterns hold anywhere
            int cellX = (int) Math.floor(camX / 256.0) * 256, cellZ = (int) Math.floor(camZ / 256.0) * 256;
            u.putMat4f(viewRotation).putVec4(a, b, clipStart, clipBand).putVec4(fogStart, fogEnd, (float) camY, seaLevel)
                    .putVec4(sun.x, sun.y, sun.z, sunStrength)
                    .putVec4((float) (camX - cellX), FarSightClient.partsOff, (float) (camZ - cellZ), FarSightClient.debug);
            for (int r = 0; r < Rings.RINGS; r++) {
                u.putVec4(originX[r], originZ[r], Rings.cellSize(r), field.valid[r] ? 1f : 0f);
            }
            for (int r = 0; r < Rings.RINGS; r++) {
                u.putIVec4((field.winX[r] * Rings.PATCH) & Rings.MASK, (field.winZ[r] * Rings.PATCH) & Rings.MASK, r * Rings.SIZE, 0);
            }
            u.putVec4(sunColour.x, sunColour.y, sunColour.z, golden).putVec4(haze, AIR_SCALE_HEIGHT, AIR_SUN, rain).putIVec4(cellX, cellZ, 0, 0)
                    .putVec4(mistFloor, mistDepth, mistDensity, mistCover);
        }
        GpuBuffer draws = drawUbo.currentBuffer();
        try (var view = draws.map(false, true)) {
            ByteBuffer d = view.data();
            for (int i = 0; i < pieces; i++) {
                int at = i * align;
                d.putInt(at, pRing[i]).putInt(at + 4, pX[i]).putInt(at + 8, pZ[i]).putInt(at + 12, pW[i]);
            }
        }
        int marks = Math.min(MAX_MARKS, landmarks.size());
        GpuBuffer markBuffer = markUbo.currentBuffer();
        if (marks > 0) {
            try (var view = markBuffer.map(false, true)) {
                ByteBuffer d = view.data();
                for (int i = 0; i < marks; i++) {
                    FarLandmarks.Landmark l = landmarks.get(i);
                    int at = i * 48;
                    d.putFloat(at, (float) (l.pos().getX() + 0.5 - camX)).putFloat(at + 4, (float) (l.pos().getY() - camY))
                            .putFloat(at + 8, (float) (l.pos().getZ() + 0.5 - camZ)).putFloat(at + 12, l.height());
                    d.putFloat(at + 16, (l.argb() >> 16 & 255) / 255f).putFloat(at + 20, (l.argb() >> 8 & 255) / 255f)
                            .putFloat(at + 24, (l.argb() & 255) / 255f).putFloat(at + 28, 3f);
                    d.putFloat(at + 32, l.from()).putFloat(at + 36, 0f).putFloat(at + 40, 0f).putFloat(at + 44, 0f);
                }
            }
        }
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "awaken far sight", main.getColorTextureView(), Optional.empty(), depthView, OptionalDouble.of(0.0))) {
            if (pieces > 0) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.TERRAIN));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("Fog", RenderSystem.getShaderFog());
                pass.setUniform("FarFrame", frame);
                pass.setUniform("HeightSampler", field.heightView, nearest);
                pass.setUniform("ColorSampler", field.colourView, nearest);
                pass.setUniform("InfoSampler", field.infoView, nearest);
                pass.setUniform("CrownSampler", field.crownView, nearest);
                pass.setUniform("GenHeightSampler", field.genHeightView, nearest);
                pass.setUniform("GenColorSampler", field.genColourView, nearest);
                pass.setUniform("GenInfoSampler", field.genInfoView, nearest);
                pass.setUniform("GenCrownSampler", field.genCrownView, nearest);
                pass.setUniform("ShadeSampler", field.shadeView, nearest);
                pass.setUniform("LightSampler", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("ChunkSampler", chunksView, nearest);
                int bound = -1;
                for (int i = 0; i < pieces; i++) {
                    int k = pW[i] / 8 - 1;
                    if (k != bound) {
                        pass.setIndexBuffer(widths[k], IndexType.SHORT);
                        bound = k;
                    }
                    pass.setUniform("FarDraw", draws.slice((long) i * align, 16));
                    pass.drawIndexed(pW[i] * pH[i] * INDICES_PER_CELL, 1, 0, 0, 0);
                }
            }
            if (treeDraws > 0) {
                GpuBuffer slots = treeSlots.currentBuffer();
                try (var view = slots.map(false, true)) {
                    ByteBuffer d = view.data();
                    for (int r = FarTrees.FIRST_RING; r <= FarTrees.LAST_RING; r++) {
                        int pb = Rings.patchBlocks(r);
                        for (int j = 0; j < Rings.PATCHES; j++) {
                            for (int i = 0; i < Rings.PATCHES; i++) {
                                int px = field.winX[r] + i, pz = field.winZ[r] + j;
                                int at = ((r - FarTrees.FIRST_RING) * Rings.PATCHES * Rings.PATCHES + Rings.slot(px, pz)) * 16;
                                d.putInt(at, px * pb).putInt(at + 4, pz * pb).putInt(at + 8, r).putInt(at + 12, 0);
                            }
                        }
                    }
                }
                pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.TREES));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("Fog", RenderSystem.getShaderFog());
                pass.setUniform("FarFrame", frame);
                pass.setUniform("Trees", field.trees);
                pass.setUniform("TreeSlots", slots);
                pass.setUniform("ShadeSampler", field.shadeView, nearest);
                pass.setUniform("GenHeightSampler", field.genHeightView, nearest);
                pass.setUniform("LightSampler", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setUniform("ChunkSampler", chunksView, nearest);
                pass.setIndexBuffer(treeIndices, IndexType.SHORT);
                if (indirect) {
                    GpuBuffer commands = treeCommands.currentBuffer();
                    try (var view = commands.map(false, true)) {
                        ByteBuffer d = view.data();
                        for (int i = 0; i < treeDraws; i++) {
                            int at = i * COMMAND_BYTES;
                            d.putInt(at, treeCount[i]).putInt(at + 4, 1).putInt(at + 8, 0).putInt(at + 12, treeVertex[i]).putInt(at + 16, 0);
                        }
                    }
                    pass.drawIndexedIndirect(commands.slice(0, (long) treeDraws * COMMAND_BYTES), treeDraws);
                } else {
                    for (int i = 0; i < treeDraws; i++) pass.drawIndexed(treeCount[i], 1, 0, treeVertex[i], 0);
                }
            }
            if (marks > 0) {
                // after the terrain, so ridges in front hide them; two quads each (core and halo)
                pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.MARKS));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("Fog", RenderSystem.getShaderFog());
                pass.setUniform("FarFrame", frame);
                pass.setUniform("FarMarks", markBuffer);
                pass.setIndexBuffer(quads, IndexType.SHORT);
                pass.drawIndexed(marks * 2 * 6, 1, 0, 0, 0);
            }
        }
        frameUbo.rotate();
        drawUbo.rotate();
        markUbo.rotate();
        treeSlots.rotate();
        treeCommands.rotate();
    }

    /** Opens this frame's GPU timing (before the bake), after reading the result of three frames ago. */
    void timeStart() {
        if (!timing) return;
        try {
            int i = timerFrame % timers.length;
            if (timers[i] == null) {
                timers[i] = RenderSystem.getDevice().createTimestampQueryPool(3);
            } else if (timerFrame >= timers.length) {
                var v = timers[i].getValues(0, 3);
                if (v[0].isPresent() && v[1].isPresent() && v[2].isPresent()) {
                    double period = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
                    bakeNanos += (v[1].getAsLong() - v[0].getAsLong()) * period;
                    gpuNanos += (v[2].getAsLong() - v[0].getAsLong()) * period;
                    gpuFrames++;
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(timers[i], 0);
        } catch (RuntimeException e) {
            timing = false;
        }
    }

    /** Between the bake and the draw: the bake's share (timestamps land at pass boundaries on Apple GPUs, as here). */
    void timeBaked() {
        if (!timing) return;
        try {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(timers[timerFrame % timers.length], 1);
        } catch (RuntimeException e) {
            timing = false;
        }
    }

    void timeEnd() {
        if (!timing) return;
        try {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(timers[timerFrame % timers.length], 2);
        } catch (RuntimeException e) {
            timing = false;
        }
        timerFrame++;
    }

    /** Mean GPU milliseconds per frame (all, and the bake's part) since the last call, or -1 if nothing was timed. */
    double[] gpuMillis() {
        double[] ms = gpuFrames == 0 ? new double[]{-1, -1} : new double[]{gpuNanos / gpuFrames / 1e6, bakeNanos / gpuFrames / 1e6};
        gpuNanos = 0;
        bakeNanos = 0;
        gpuFrames = 0;
        return ms;
    }

    /** The depth texture Lens reads (null before the first frame). */
    GpuTexture depthTexture() {
        return depth;
    }

    int memoryBytes() {
        return depth == null ? 0 : depth.getWidth(0) * depth.getHeight(0) * 4;
    }

    @Override
    public void close() {
        frameUbo.close();
        drawUbo.close();
        markUbo.close();
        for (GpuBuffer b : widths) b.close();
        quads.close();
        treeIndices.close();
        treeSlots.close();
        treeCommands.close();
        chunksView.close();
        chunks.close();
        MemoryUtil.memFree(chunkBytes);
        for (GpuQueryPool t : timers) if (t != null) t.close();
        if (depthView != null) depthView.close();
        if (depth != null) depth.close();
        depth = null;
        depthView = null;
    }
}
