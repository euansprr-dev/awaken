package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Lens's per-frame values as one uniform block (shaders/include/lens_block.glsl; SPEC §22, Lens on Apple): the frame's
 * 48 texels (LensFrame), the look's 32 parameters as the floats a texture fetch of them gives (byte / 255, LensParams)
 * and a flag telling the shaders to use it. Tiles' hook binds it in place of the chain's zeros, so the shaders read
 * values every pixel shares from a constant load instead of a texture fetch per pixel per use. Written once a frame,
 * after both textures are; nothing allocated per frame.
 */
final class LensBlock {
    static final int VEC4S = LensFrame.WIDTH + LensParams.WIDTH + 1;
    final GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "awaken lens block", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
            (long) VEC4S * 16);
    private final ByteBuffer data = MemoryUtil.memCalloc(VEC4S * 16);

    /** This frame's values (the frame's only on Cinematic and Ultra: Swift's shaders don't read them). */
    void write(@Nullable LensFrame frame, LensParams params) {
        if (frame != null) frame.copyTo(data, 0);
        params.copyTo(data, LensFrame.WIDTH * 16);
        int use = (LensFrame.WIDTH + LensParams.WIDTH) * 16;
        data.putFloat(use, 1f).putFloat(use + 4, 0f).putFloat(use + 8, 0f).putFloat(use + 12, 0f);
        data.clear();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
    }

    void close() {
        buffer.close();
        MemoryUtil.memFree(data);
    }
}
