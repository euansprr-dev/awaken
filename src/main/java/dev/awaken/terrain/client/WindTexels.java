package dev.awaken.terrain.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The wind, handed to Awaken's terrain shader through 4 texels in the block atlas's bottom-right corner (Sodium binds
 * only its own uniforms, the atlas and the lightmap; the atlas is the one thing Awaken can write that the shader can
 * read). The stitcher fills the atlas from the top left, so that corner lies outside every sprite; it is checked at
 * each stitch and nothing is written if a sprite is there. Written once a frame, before terrain draws.
 *
 * <pre>
 * texel 0  "AWND" signature
 * texel 1  time in seconds modulo 256 (float bits)
 * texel 2  camera x, y, z in 16-block steps modulo 256 (so modulo 4096 blocks), amplitude (x 0.25 blocks)
 * texel 3  wind direction x, z (-1..1 as 0..1), steady strength, gust
 * </pre>
 * The shader only needs the camera to within a few blocks: it rounds (region offset + camera) to whole render regions
 * to find each region's world position modulo 4096. Four fetches per leaf vertex, one for every other vertex none.
 * Wind is a function of the world's game time only (the same for every player, no packets): a direction that drifts
 * over minutes, a steady strength that breathes over a minute or two and rises with rain and thunder, and gusts.
 * Stand-in for the terrain module's wind model (SPEC 15), which will feed the same texels.
 */
final class WindTexels {
    private WindTexels() {}

    static final int TEXELS = 4;
    private static final int SIGNATURE = 0x444E5741; // "AWND" little-endian
    /** Full sway at steady 1 and gust 1, in blocks (a few hundredths in a breeze). */
    private static final float AMPLITUDE = 0.11F;

    private static final ByteBuffer BUF = MemoryUtil.memAlloc(TEXELS * 4).order(ByteOrder.LITTLE_ENDIAN);
    private static GpuTexture cornerOf; // the atlas texture whose corner was found free
    private static boolean cornerFree;
    /** Measuring aid ({@code /awaken terrain waving debug}): 0 normal, 1 no signature (shader exits early), 2 amplitude 0. */
    static int debugMode;

    /** At each block-atlas stitch: is the corner strip outside every sprite? */
    static void onBlockAtlasStitched(TextureAtlas atlas) {
        GpuTexture tex = atlas.getTexture();
        int w = tex.getWidth(0), h = tex.getHeight(0);
        int x0 = w - TEXELS, y0 = h - 1;
        boolean free = true;
        for (TextureAtlasSprite s : atlas.getTextures().values()) {
            int pad = Math.round(s.getU0() * w) - s.getX(); // sprites carry a border for filtering
            int x1 = s.getX() + s.contents().width() + 2 * pad, y1 = s.getY() + s.contents().height() + 2 * pad;
            if (x1 > x0 && y1 > y0) {
                free = false;
                break;
            }
        }
        cornerOf = tex;
        cornerFree = free;
        if (free) Awaken.LOG.info("Waving leaves: wind texels at {},{} of the {}x{} block atlas", x0, y0, w, h);
        else Awaken.LOG.warn("Waving leaves: the block atlas's corner holds a sprite; leaves will not wave this session");
    }

    static boolean cornerFree() {
        return cornerFree;
    }

    /** Writes this frame's wind. Render thread, before the level draws. */
    static void write(Minecraft mc, float partialTick) {
        ClientLevel level = mc.level;
        if (level == null || !cornerFree) return;
        GpuTexture tex = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTexture();
        if (tex != cornerOf) return; // replaced by a reload whose stitch we have not seen yet
        double ticks = (level.getGameTime() % (256L * 20L)) + partialTick;
        double gameSeconds = (level.getGameTime() + partialTick) / 20.0;
        float t = (float) (ticks / 20.0);
        Vec3 cam = mc.gameRenderer.mainCamera().position();

        // direction: a seeded heading per dimension that wanders over minutes
        double heading = (level.dimension().identifier().hashCode() & 0xFFFF) / 65536.0 * Mth.TWO_PI
                + 0.9 * Math.sin(gameSeconds / 311.0) + 0.4 * Math.sin(gameSeconds / 97.0 + 1.3);
        float steady = (float) (0.45 + 0.25 * noise(gameSeconds / 70.0, 11) + 0.3 * level.getRainLevel(partialTick)
                + 0.25 * level.getThunderLevel(partialTick));
        float gust = (float) Math.pow(Math.max(0.0, noise(gameSeconds / 7.0, 23) * 1.4 - 0.25), 1.3);

        ByteBuffer b = BUF;
        b.clear();
        b.putInt(debugMode == 1 ? 0 : SIGNATURE);
        b.putFloat(t);
        b.put(step16(cam.x)).put(step16(cam.y)).put(step16(cam.z)).put(debugMode == 2 ? 0 : unorm(AMPLITUDE / 0.25F));
        b.put(snorm((float) Math.cos(heading))).put(snorm((float) Math.sin(heading))).put(unorm(steady)).put(unorm(gust));
        b.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(tex, b, 0, 0, tex.getWidth(0) - TEXELS, tex.getHeight(0) - 1, TEXELS, 1);
    }

    /** A coordinate in 16-block steps modulo 256 (the shader adds half a step back). */
    private static byte step16(double v) {
        return (byte) Math.floorMod((long) Math.floor(v / 16.0), 256L);
    }

    private static byte unorm(float v) {
        return (byte) Math.round(Mth.clamp(v, 0.0F, 1.0F) * 255.0F);
    }

    private static byte snorm(float v) {
        return unorm(v * 0.5F + 0.5F);
    }

    /** Smooth value noise in 0..1 over time, seeded. */
    private static double noise(double x, int seed) {
        double i = Math.floor(x);
        double f = x - i;
        double u = f * f * (3.0 - 2.0 * f);
        return Mth.lerp(u, hash((long) i, seed), hash((long) i + 1, seed));
    }

    private static double hash(long n, int seed) {
        long h = n * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
