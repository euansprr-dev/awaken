package dev.awaken.lens.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * The 32×1 texture Lens's shaders read their parameters from (post chains only take static uniforms, so values that
 * change every frame travel this way). Each texel is four 8-bit values; the layout is documented in
 * {@code lens_composite.fsh}. Uploads only when a byte actually changed.
 */
final class LensParams {
    static final int WIDTH = 32;
    final DynamicTexture texture;
    private final int[] packed = new int[WIDTH], uploaded = new int[WIDTH];
    private boolean dirty = true;

    LensParams() {
        texture = new DynamicTexture(() -> "awaken lens params", WIDTH, 1, true);
    }

    void write(int debug, float[] look, float far, boolean zeroToOne, float sunU, float sunV, float sunVisible, float sunsetStrength,
            float sunsetR, float sunsetG, float sunsetB, float fogR, float fogG, float fogB, float fogEnd, float environmentalEnd,
            float tear, float crush, float bloomBoost, float focus, float motion, float motionX, float motionY,
            boolean farActive, boolean farReadable, boolean farReverseZ, boolean farZeroToOne, float farNear, float farFar) {
        int f = Math.clamp(Math.round(far), 0, 65535);
        int u = Math.clamp(Math.round((sunU + 1f) / 3f * 65535f), 0, 65535), v = Math.clamp(Math.round((sunV + 1f) / 3f * 65535f), 0, 65535);
        // texel 0: b = far terrain drawn, a = its depth (bit 0 reverse-Z, bit 1 zero-to-one, bit 2 shared with Lens; the
        // shaders trust a shared depth once it proves itself, pass lens_far_probe)
        packed[0] = bytes(debug == 0 ? 255 : debug, zeroToOne ? 255 : 0, farActive ? 255 : 0,
                (farReverseZ ? 1 : 0) | (farZeroToOne ? 2 : 0) | (farReadable ? 4 : 0));
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
        // 13: the story's moments (LensClient.override/tear): r the tear from black (0 off, 1..255 its progress),
        // g crushed blacks, b extra bloom
        int t = tear <= 0f ? 0 : 1 + Math.clamp(Math.round(tear * 254f), 0, 254);
        packed[13] = bytes(t, b(crush), b(bloomBoost), 0);
        // 14: r how much an open holo panel holds the eye (UiHooks.focus): the world behind it dims
        packed[14] = pack(focus, 0f, 0f, 0f);
        // 15: far terrain's near plane (1/16 block) and far plane (blocks)
        int fn = Math.clamp(Math.round(farNear * 16f), 0, 65535), ff = Math.clamp(Math.round(farFar), 0, 65535);
        packed[15] = bytes(fn >> 8, fn & 255, ff >> 8, ff & 255);
        // 16: moving fast through the air (LensClient.motion): r strength, g/b the heading on screen (0..1, from the
        // bottom left)
        packed[16] = pack(motion, motionX, motionY, 0f);
        // 17-18: Lens 2's light (SPEC §22): r light, g shade, b ambient occlusion, a filmic; then the shade's tint (/2, so
        // a cool tint can lift blue a little) and the key light's warmth
        packed[17] = pack(look[Look.LIGHT], look[Look.SHADE], look[Look.AO], look[Look.FILMIC]);
        packed[18] = pack(look[Look.SHADE_TINT] / 2f, look[Look.SHADE_TINT + 1] / 2f, look[Look.SHADE_TINT + 2] / 2f, look[Look.KEY_WARMTH]);
        // 19: Lens 2's atmosphere: r mist, g its scale height (/256 blocks), b fog glow (/2), a sky glow
        packed[19] = pack(look[Look.MIST], look[Look.MIST_HEIGHT] / 256f, look[Look.FOG_GLOW] / 2f, look[Look.SKY_GLOW]);
        // 20: water and magic: r reflections on water, g heat shimmer, b frost at the edges, a emissive glow; 21-31 free
        packed[20] = pack(look[Look.WATER], look[Look.HEAT], look[Look.FROST], look[Look.GLOW]);
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
