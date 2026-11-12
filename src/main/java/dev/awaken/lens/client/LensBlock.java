package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Lens's per-frame values as one uniform block (shaders/include/lens_block.glsl; SPEC §22, Lens on Apple): the frame's
 * 64 texels (LensFrame), the look's 32 parameters as the floats a texture fetch of them gives (byte / 255, LensParams)
 * and a flag telling the shaders to use it. Tiles' hook binds it in place of the chain's zeros, so the shaders read
 * values every pixel shares from a constant load instead of a texture fetch per pixel per use. Written once a frame,
 * after both textures are; nothing allocated per frame.
 */
final class LensBlock {
    static final int VEC4S = LensFrame.WIDTH + LensParams.WIDTH + 1;
    final GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "awaken lens block", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
            (long) VEC4S * 16);
    private final ByteBuffer data = MemoryUtil.memCalloc(VEC4S * 16);

    /**
     * This frame's values (the frame's only on Cinematic and Ultra: Swift's shaders don't read them). Returns whether the
     * frame is calm (see {@link #calm}).
     */
    boolean write(@Nullable LensFrame frame, LensParams params) {
        if (frame != null) frame.copyTo(data, 0);
        params.copyTo(data, LensFrame.WIDTH * 16);
        int use = (LensFrame.WIDTH + LensParams.WIDTH) * 16;
        data.putFloat(use, 1f).putFloat(use + 4, 0f).putFloat(use + 8, 0f).putFloat(use + 12, 0f);
        data.clear();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        return frame != null && calm(data);
    }

    private static float f(ByteBuffer d, int texel, int c) {
        return d.getFloat(texel * 16 + c * 4);
    }

    private static float p(ByteBuffer d, int texel, int c) {
        return d.getFloat((LensFrame.WIDTH + texel) * 16 + c * 4);
    }

    /**
     * Whether every path the calm composite leaves out (LENS_CALM in lens_composite.glsl) is off for these values: each
     * test is the composite's own, on the floats it reads from this block (none of them in the texels the world's
     * matrices are copied over). A frame where any is on runs the twin's own composite (LensClient.leftOut).
     */
    static boolean calm(ByteBuffer d) {
        return !(f(d, 16, 2) > 0f && f(d, 15, 0) > 0f)        // rain's wet (f16.z, F(15).x)
                && !(p(d, 20, 0) > 0f && f(d, 22, 3) <= 0f)   // the old water (P(20).r, without the Air: airOn() = F(22).w)
                && !(p(d, 20, 1) > 0f || p(d, 20, 2) > 0f)    // heat's shimmer and frost's rime (P(20).g, .b)
                && !(p(d, 16, 0) > 0.004f)                    // the motion smear (P(16).r)
                && (int) (f(d, 20, 1) + 0.5f) == 0            // storm cells (stormCount(), F(20).y)
                && !(f(d, LensFrame.HAZE, 3) > 0f)            // dust or fog as air (hazeAir, F(48).w)
                && !(p(d, 14, 0) > 0f)                        // a holo panel's focus (P(14).r)
                && !(p(d, 13, 0) > 0f);                       // the tear (P(13).r)
    }

    void close() {
        buffer.close();
        MemoryUtil.memFree(data);
    }
}
