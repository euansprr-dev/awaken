package dev.awaken.lens.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * The 16×1 texture Lens's shaders read their parameters from (post chains only take static uniforms, so values that
 * change every frame travel this way). Each texel is four 8-bit values; the layout is documented in
 * {@code lens_composite.fsh}. Uploads only when a byte actually changed.
 */
final class LensParams {
    static final int WIDTH = 16;
    final DynamicTexture texture;
    private final int[] packed = new int[WIDTH], uploaded = new int[WIDTH];
    private boolean dirty = true;

    LensParams() {
        texture = new DynamicTexture(() -> "awaken lens params", WIDTH, 1, true);
    }

    void write(float[] look, float far, boolean zeroToOne, float sunU, float sunV, float sunVisible, float sunsetStrength,
            float sunsetR, float sunsetG, float sunsetB, float fogR, float fogG, float fogB, float fogEnd, float environmentalEnd) {
        int f = Math.clamp(Math.round(far), 0, 65535);
        int u = Math.clamp(Math.round((sunU + 1f) / 3f * 65535f), 0, 65535), v = Math.clamp(Math.round((sunV + 1f) / 3f * 65535f), 0, 65535);
        packed[0] = pack(1f, zeroToOne ? 1f : 0f, 0f, 0f);
        packed[1] = bytes(f >> 8, f & 255, b(sunVisible), b(sunsetStrength));
        packed[2] = bytes(u >> 8, u & 255, v >> 8, v & 255);
        packed[3] = pack(look[Look.EXPOSURE] / 2f, look[Look.CONTRAST] / 2f, look[Look.SATURATION] / 2f, look[Look.WARMTH] * 0.5f + 0.5f);
        packed[4] = pack(look[Look.SHADOW_TINT], look[Look.SHADOW_TINT + 1], look[Look.SHADOW_TINT + 2], look[Look.SPLIT_TONE]);
        packed[5] = pack(look[Look.HIGHLIGHT_TINT], look[Look.HIGHLIGHT_TINT + 1], look[Look.HIGHLIGHT_TINT + 2], look[Look.VIGNETTE]);
        packed[6] = pack(look[Look.BLOOM] / 2f, look[Look.BLOOM_THRESHOLD], look[Look.SKY_BLOOM], look[Look.SHAFTS]);
        packed[7] = pack(look[Look.HAZE], look[Look.HAZE_START], look[Look.HAZE_TINT_MIX], look[Look.HAZE_DESATURATE]);
        packed[8] = pack(look[Look.HAZE_TINT], look[Look.HAZE_TINT + 1], look[Look.HAZE_TINT + 2], look[Look.SUNSET_WARMTH]);
        packed[9] = pack(sunsetR, sunsetG, sunsetB, 0f);
        packed[10] = pack(fogR, fogG, fogB, 1f);
        int rd = Math.clamp(Math.round(fogEnd), 0, 65535), env = Math.clamp(Math.round(environmentalEnd), 0, 65535);
        packed[11] = bytes(rd >> 8, rd & 255, env >> 8, env & 255);
        packed[12] = pack(look[Look.SUN_GLOW], 0f, 0f, 0f);
        for (int i = 0; i < WIDTH; i++) {
            if (packed[i] != uploaded[i]) {
                dirty = true;
                break;
            }
        }
    }

    void uploadIfChanged() {
        if (!dirty) return;
        NativeImage px = texture.getPixels();
        for (int i = 0; i < WIDTH; i++) {
            px.setPixelABGR(i, 0, packed[i]);
            uploaded[i] = packed[i];
        }
        texture.upload();
        dirty = false;
    }

    private static int b(float v) {
        return Math.clamp(Math.round(v * 255f), 0, 255);
    }

    private static int pack(float r, float g, float bl, float a) {
        return bytes(b(r), b(g), b(bl), b(a));
    }

    private static int bytes(int r, int g, int bl, int a) {
        return a << 24 | bl << 16 | g << 8 | r; // NativeImage stores ABGR
    }
}
