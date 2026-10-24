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
import dev.awaken.farsight.Rings;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the rings (SPEC §16) right after the sky, before any chunk: real chunks then simply draw over far terrain, so
 * the two never fight over depth. Every ring is the same grid; its window, minus the window of the ring inside it, is
 * cut into pieces of at most 64×64 cells, and a piece is drawn only if it is in view and not entirely under the real
 * chunks. One shared index buffer serves every piece: cell {@code i} of a piece is vertices {@code 12i..12i+11}, a
 * flat top and the two walls that can face the camera, each worked out in the vertex shader from the atlases.
 *
 * <p>Far Sight keeps its own depth (reversed-Z, the device's range, near {@link #NEAR}, far {@link #FAR}) in a texture
 * of the main target's size. Nothing else writes it, so it lasts until post effects, where Lens reads it through
 * compat.client.FarTerrain to haze far terrain by its real distance.
 */
final class FarRenderer implements AutoCloseable {
    static final float NEAR = 1f, FAR = 32768f;
    static final int PIECE = 64, MAX_DRAWS = 256;
    private static final int QUADS_PER_CELL = 3, INDICES_PER_CELL = QUADS_PER_CELL * 6;
    static final int FRAME_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get()
            + Rings.RINGS * 16 * 2;

    private final MappableRingBuffer frameUbo = new MappableRingBuffer(() -> "awaken far sight frame", 130, FRAME_SIZE);
    private final int align = Math.max(16, RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());
    private final MappableRingBuffer drawUbo = new MappableRingBuffer(() -> "awaken far sight draws", 130, MAX_DRAWS * align);
    private final GpuBuffer indices;
    private GpuTexture depth;
    private GpuTextureView depthView;

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
    private double gpuNanos;
    private int gpuFrames;

    FarRenderer() {
        int cellsPerPiece = PIECE * PIECE;
        ByteBuffer data = MemoryUtil.memAlloc(cellsPerPiece * INDICES_PER_CELL * 2).order(ByteOrder.nativeOrder());
        for (int q = 0; q < cellsPerPiece * QUADS_PER_CELL; q++) {
            int v = q * 4;
            data.putShort((short) v).putShort((short) (v + 1)).putShort((short) (v + 2))
                    .putShort((short) (v + 2)).putShort((short) (v + 3)).putShort((short) v);
        }
        data.flip();
        indices = RenderSystem.getDevice().createBuffer(() -> "awaken far sight quads", GpuBuffer.USAGE_INDEX, data);
        MemoryUtil.memFree(data);
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
        for (int pz = z / Rings.PATCH; pz <= (z + h - 1) / Rings.PATCH; pz++) {
            for (int px = x / Rings.PATCH; px <= (x + w - 1) / Rings.PATCH; px++) {
                int wx = field.winX[r] + px, wz = field.winZ[r] + pz;
                if (!field.has(r, wx, wz)) return Long.MIN_VALUE;
                int slot = Rings.slot(wx, wz);
                lo = Math.min(lo, (field.lo[r][slot] & 0xFFFF) - FarColumns.HEIGHT_OFFSET);
                hi = Math.max(hi, (field.hi[r][slot] & 0xFFFF) - FarColumns.HEIGHT_OFFSET);
            }
        }
        // walls reach down to a lower neighbour, possibly in the next ring: a little slack below
        return (long) (lo - 64) << 32 | (hi + 1) & 0xFFFFFFFFL;
    }

    /**
     * Draws the planned pieces into the main colour target and Far Sight's depth. {@code sun} is the light's direction
     * (world space); {@code fogStart}/{@code fogEnd} Far Sight's own fog at the horizon; {@code clipStart}/{@code clipBand}
     * where it dithers in under the real chunks.
     */
    void draw(FarField field, RenderTarget main, Matrix4fc viewRotation, double camY, Vector3f sun, float fogStart, float fogEnd,
            float clipStart, float clipBand, int seaLevel, boolean zeroToOne, GpuTextureView lightmap) {
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
            u.putMat4f(viewRotation).putVec4(a, b, clipStart, clipBand).putVec4(fogStart, fogEnd, (float) camY, seaLevel)
                    .putVec4(sun.x, sun.y, sun.z, 0f);
            for (int r = 0; r < Rings.RINGS; r++) {
                u.putVec4(originX[r], originZ[r], Rings.cellSize(r), field.valid[r] ? 1f : 0f);
            }
            for (int r = 0; r < Rings.RINGS; r++) {
                u.putIVec4((field.winX[r] * Rings.PATCH) & Rings.MASK, (field.winZ[r] * Rings.PATCH) & Rings.MASK, r * Rings.SIZE, 0);
            }
        }
        GpuBuffer draws = drawUbo.currentBuffer();
        try (var view = draws.map(false, true)) {
            ByteBuffer d = view.data();
            for (int i = 0; i < pieces; i++) {
                int at = i * align;
                d.putInt(at, pRing[i]).putInt(at + 4, pX[i]).putInt(at + 8, pZ[i]).putInt(at + 12, pW[i]);
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
                pass.setUniform("ShadeSampler", field.shadeView, nearest);
                pass.setUniform("GenHeightSampler", field.genHeightView, nearest);
                pass.setUniform("GenColorSampler", field.genColourView, nearest);
                pass.setUniform("LightSampler", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setIndexBuffer(indices, IndexType.SHORT);
                for (int i = 0; i < pieces; i++) {
                    pass.setUniform("FarDraw", draws.slice((long) i * align, 16));
                    pass.drawIndexed(pW[i] * pH[i] * INDICES_PER_CELL, 1, 0, 0, 0);
                }
            }
        }
        frameUbo.rotate();
        drawUbo.rotate();
    }

    /** Opens this frame's GPU timing (before the bake), after reading the result of three frames ago. */
    void timeStart() {
        if (!timing) return;
        try {
            int i = timerFrame % timers.length;
            if (timers[i] == null) {
                timers[i] = RenderSystem.getDevice().createTimestampQueryPool(2);
            } else if (timerFrame >= timers.length) {
                var v = timers[i].getValues(0, 2);
                if (v[0].isPresent() && v[1].isPresent()) {
                    gpuNanos += (v[1].getAsLong() - v[0].getAsLong()) * (double) RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
                    gpuFrames++;
                }
            }
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(timers[i], 0);
        } catch (RuntimeException e) {
            timing = false;
        }
    }

    void timeEnd() {
        if (!timing) return;
        try {
            RenderSystem.getDevice().createCommandEncoder().writeTimestamp(timers[timerFrame % timers.length], 1);
        } catch (RuntimeException e) {
            timing = false;
        }
        timerFrame++;
    }

    /** Mean GPU milliseconds per frame since the last call, or -1 if nothing was timed. */
    double gpuMillis() {
        double ms = gpuFrames == 0 ? -1 : gpuNanos / gpuFrames / 1e6;
        gpuNanos = 0;
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
        indices.close();
        for (GpuQueryPool t : timers) if (t != null) t.close();
        if (depthView != null) depthView.close();
        if (depth != null) depth.close();
        depth = null;
        depthView = null;
    }
}
