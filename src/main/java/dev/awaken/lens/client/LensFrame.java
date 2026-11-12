package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Lens 2's per-frame values that need full float precision (SPEC §22): the frame's view-projection (camera-relative),
 * its inverse, the previous frame's (for temporal accumulation), the sun on screen, the camera within the heightfield's
 * ring and the light's direction. The matrices and the sun written here are the CPU's estimate (the game's projection
 * with its own bob, hurt tilt and nausea); LensClient then copies WorldProjection's exact ones over them on the GPU,
 * from the projection the world was really drawn with (a camera mod's roll included).
 * A 48×1 RGBA32F texture ({@code awaken:lens_frame}), written once a frame just before the post effects run; layout in
 * {@code shaders/include/lens_frame.glsl}. Nothing is allocated per frame.
 */
final class LensFrame {
    static final int WIDTH = 48;
    /** The sun on screen (layout in lens_frame.glsl). */
    static final int SUN = 21;
    final Texture texture = new Texture();
    private final ByteBuffer data = MemoryUtil.memAlloc(WIDTH * 16);
    private final Matrix4f proj = new Matrix4f(), bob = new Matrix4f(), viewProj = new Matrix4f(), inverse = new Matrix4f(),
            prevViewProj = new Matrix4f();
    private final Vector3f spinAxis = new Vector3f();
    private double prevX, prevY, prevZ;
    private long prevNs;
    private final long start = System.nanoTime();
    private int frame;
    private boolean hasPrev;
    private float sunU = 0.5f, sunV = 0.5f, sunFront;

    private float cloudBase, cloudCover;
    // the Holo world plates drawn this frame (HoloWorld.lensPlates): Lens keeps its light, AO and haze off them
    private final float[] plates = new float[dev.awaken.holo.client.HoloWorld.MAX * 9];
    private int plateCount;
    private double cloudDrift;
    private int cloudColour;
    // the Living Wilds' storm cells this frame (StormSky.lensCells), marched in the cloud pass
    private final float[] storms = new float[dev.awaken.living.client.StormSky.MAX * dev.awaken.living.pulse.StormCell.FLOATS];

    // the Air (SPEC §22 "Steady Light"): the sun's own direction (under the horizon by night), how much of the Air is on,
    // the game's sky colour this frame and its stars' brightness
    private float airSunX, airSunY = 1f, airSunZ, airOn, skyR, skyG, skyB, stars;
    // a land with a sky of its own (lens_air.glsl, airLand): its horizon's colour, and how far the sky is the land's
    private float landR, landG, landB, landK;

    /** This frame's land (SPEC §22 "The Air in other worlds"): its horizon's colour, and 1 where the Air draws the land's own sky. */
    void land(float r, float g, float b, float k) {
        landR = r;
        landG = g;
        landB = b;
        landK = k;
    }

    /** This frame's Air: toward the sun, its strength (0: off), the game's sky colour and star brightness. */
    void air(float sunX, float sunY, float sunZ, float on, float r, float g, float b, float starBrightness) {
        airSunX = sunX;
        airSunY = sunY;
        airSunZ = sunZ;
        airOn = on;
        skyR = r;
        skyG = g;
        skyB = b;
        stars = starBrightness;
    }

    // the Sun Pass's block map: how far from the camera it can be trusted (0: none), and its lowest y
    private float blocksReach, blocksMinY;

    /** This frame's block map (LensBlocks): its trusted reach in blocks (0: not used) and its lowest y. */
    void blocks(float reach, float minY) {
        blocksReach = reach;
        blocksMinY = minY;
    }

    // the lit air (LensLit): its volume's corner in the world, and whether the texture holds one
    private int litX, litY, litZ;
    private boolean litOn;

    /** This frame's lit air: the volume's lowest block on each axis, and whether to read it. */
    void lit(int x, int y, int z, boolean on) {
        litX = x;
        litY = y;
        litZ = z;
        litOn = on;
    }

    // development (/awaken lens paths): the light pass marks which part of the Sun Pass answered each pixel
    private boolean paths;

    void paths(boolean on) {
        paths = on;
    }

    // the small shade's reach (texels 30 to 33, w): how wide a texel of the light buffer may be on a surface, in the
    // art's pixels (sixteenths of a block), before the depth-made contact shadows and the ambient shade fade out, each
    // a start and an end (an end not past its start: no fade)
    private float contactFrom, contactTo, ambientFrom, ambientTo;

    void small(float contactFrom, float contactTo, float ambientFrom, float ambientTo) {
        this.contactFrom = contactFrom;
        this.contactTo = contactTo;
        this.ambientFrom = ambientFrom;
        this.ambientTo = ambientTo;
    }

    /** This frame's clouds (L4): the layer's base height (0: Lens draws none), cover 0..1, wind drift in blocks, colour. */
    void clouds(float base, float cover, double drift, int colour) {
        cloudBase = base;
        cloudCover = cover;
        cloudDrift = drift;
        cloudColour = colour;
    }

    /** The sun's place on screen by the CPU's estimate (uv; {@code front} whether it is ahead of the camera). */
    void sun(float u, float v, boolean front) {
        sunU = u;
        sunV = v;
        sunFront = front ? 1f : 0f;
    }

    /** Forget the previous frame (a new chain, a new place): the next frame starts its history fresh. */
    void reset() {
        hasPrev = false;
    }

    /**
     * Writes and uploads this frame's values. {@code lightX..Z} points toward the sun (or the moon at night),
     * {@code light} 0..1 how much it lights the world (0: no sun model here), {@code moon} whether it's the moon.
     */
    void write(CameraRenderState cam, PlayerRenderState player, boolean zeroToOne, float lightX, float lightY, float lightZ,
            float light, boolean moon, float rain, LensHeightfield height, boolean temporal) {
        Minecraft mc = Minecraft.getInstance();
        // the projection GameRenderer.renderLevel draws the world with: its bob and hurt tilt, then the nausea warp
        proj.set(cam.projectionMatrix).mul(WorldProjection.vanillaBob(cam, bob));
        float screenEffect = mc.options.screenEffectScale().get().floatValue();
        float spin = Math.max(player.portalEffectIntensity, player.nauseaEffectIntensity) * (screenEffect * screenEffect);
        if (spin > 0f) {
            float skew = 5.0F / (spin * spin + 5.0F) - spin * 0.04F;
            skew *= skew;
            spinAxis.set(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float angle = player.spinningEffectAngle * (float) (Math.PI / 180.0);
            proj.rotate(angle, spinAxis).scale(1.0F / skew, 1.0F, 1.0F).rotate(-angle, spinAxis);
        }
        viewProj.set(proj).mul(cam.viewRotationMatrix);
        viewProj.invert(inverse);

        double x = cam.pos.x, y = cam.pos.y, z = cam.pos.z;
        long now = System.nanoTime();
        // history holds across ordinary frames; not across a teleport, a long gap or a fresh start
        // (and where the world's own matrices replace these, only when theirs carry last frame's too)
        boolean valid = hasPrev && now - prevNs < 250_000_000L && Math.abs(x - prevX) + Math.abs(y - prevY) + Math.abs(z - prevZ) < 24.0
                && (!WorldProjection.ready() || WorldProjection.history());
        if (!valid) prevViewProj.set(viewProj);

        data.clear();
        putMatrix(0, inverse);
        putMatrix(4, viewProj);
        putMatrix(8, prevViewProj);
        float ring = LensHeightfield.SIZE;
        put(12, (float) (x - Math.floor(x / ring) * ring), (float) y, (float) (z - Math.floor(z / ring) * ring), frame);
        put(13, valid ? (float) (x - prevX) : 0f, valid ? (float) (y - prevY) : 0f, valid ? (float) (z - prevZ) : 0f, valid ? 1f : 0f);
        put(14, lightX, lightY, lightZ, light);
        // (15 w, 20 zw and 28 w: the lit air, whether it is there and the camera's place in its volume, in blocks from
        // its corner. Its z rode in texel 21's w, and the world's own projection copies the whole of texel 21 over
        // this one every frame (LensClient; its w is 0): the shader took the camera to stand at the volume's z edge,
        // so every surface more than two blocks to the camera's +z was judged by a slice of air some 70 blocks away,
        // open sky as a rule, and "sure of the sun" there: no shadow. A tree's shadow was whole from one side and gone
        // from the other, with a straight edge that followed the camera, Euan 11 and 12 Nov. Nothing rides on a texel
        // that anything else writes.)
        float lcx = (float) (x - litX), lcy = (float) (y - litY), lcz = (float) (z - litZ);
        put(15, height != null ? ring : 0f, height != null ? height.maxTop() : -64f, LensHeightfield.TRUSTED, litOn && height != null ? 1f : 0f);
        put(16, zeroToOne ? 1f : 0f, moon ? 1f : 0f, rain, 0.05f);
        // 17: x seconds (wrapping every hour) for what moves on its own: ripples, heat shimmer, frost; y 1 when the clouds
        // and light are spread over frames (lensTemporal)
        // z, w: the Sun Pass's block map (its trusted reach, 0 without one; its lowest y)
        put(17, (float) ((now - start) / 1.0e9 % 3600.0), temporal ? 1f : 0f, blocksReach, blocksMinY);
        // 18-19: the clouds: base height (0 none), cover, the camera's x and z plus the wind's drift (modulo the noise's
        // 30720-block repeat); their colour (the game's, by time and weather) and alpha
        double period = 30720.0;
        put(18, cloudBase, cloudCover, (float) floorMod(x + cloudDrift, period), (float) floorMod(z, period));
        put(19, ((cloudColour >> 16) & 255) / 255f, ((cloudColour >> 8) & 255) / 255f, (cloudColour & 255) / 255f, ((cloudColour >>> 24) & 255) / 255f);
        // 20: x the Holo world plates drawn this frame (up to 6); 24-41: each plate's camera-relative centre, half its
        // width and half its height as vectors (3 texels a plate)
        plateCount = dev.awaken.holo.client.HoloWorld.lensPlates(plates);
        int stormCount = dev.awaken.living.client.StormSky.lensCells(storms, x, y, z);
        put(20, plateCount, stormCount, lcx, lcy);
        // 21: the sun on screen (uv; z 1 when it is ahead), the estimate WorldProjection's replaces: all four
        // components of it, so nothing else lives here
        put(SUN, sunU, sunV, sunFront, 0f);
        // 22-23: the Air (lens_air.glsl)
        put(22, airSunX, airSunY, airSunZ, airOn);
        put(23, skyR, skyG, skyB, stars);
        for (int k = 0; k < dev.awaken.holo.client.HoloWorld.MAX; k++) {
            int o = k * 9, t = 24 + k * 3;
            boolean on = k < plateCount;
            // (the plates' fourth components are spare: texels 24, 25, 26 carry the land's horizon and 27 its share;
            // 28 the camera's z in the lit air's volume; 29 the development view of the Sun Pass's parts; 30 to 33 the
            // small shade's reach: the contact shadows' fade, then the ambient shade's)
            put(t, on ? plates[o] : 0f, on ? plates[o + 1] : 0f, on ? plates[o + 2] : 0f,
                    k == 0 ? landR : k == 1 ? landK : k == 2 ? contactFrom : k == 3 ? ambientTo : 0f);
            put(t + 1, on ? plates[o + 3] : 0f, on ? plates[o + 4] : 0f, on ? plates[o + 5] : 0f, k == 0 ? landG : k == 1 ? lcz : k == 2 ? contactTo : 0f);
            put(t + 2, on ? plates[o + 6] : 0f, on ? plates[o + 7] : 0f, on ? plates[o + 8] : 0f,
                    k == 0 ? landB : k == 1 ? (paths ? 1f : 0f) : k == 2 ? ambientFrom : 0f);
        }
        // 42-47: up to two storm cells, three texels each (layout in lens_storm_frame.glsl)
        for (int k = 0; k < dev.awaken.living.client.StormSky.MAX; k++) {
            int o = k * dev.awaken.living.pulse.StormCell.FLOATS, t = 42 + k * 3;
            for (int j = 0; j < 3; j++) {
                boolean on = k < stormCount;
                put(t + j, on ? storms[o + j * 4] : 0f, on ? storms[o + j * 4 + 1] : 0f, on ? storms[o + j * 4 + 2] : 0f, on ? storms[o + j * 4 + 3] : 0f);
            }
        }
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), data, 0, 0, 0, 0, WIDTH, 1);

        prevViewProj.set(viewProj);
        prevX = x;
        prevY = y;
        prevZ = z;
        prevNs = now;
        hasPrev = true;
        frame = (frame + 1) & 0xFFFF;
    }

    /** This frame's texels into {@code dst} at byte {@code at} (LensBlock). */
    void copyTo(ByteBuffer dst, int at) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(dst) + at, (long) WIDTH * 16);
    }

    private static double floorMod(double v, double m) {
        return v - Math.floor(v / m) * m;
    }

    void close() {
        texture.close();
        MemoryUtil.memFree(data);
    }

    private void putMatrix(int texel, Matrix4f m) {
        // column by column: the shader builds mat4(c0, c1, c2, c3)
        m.get(texel * 16, data);
    }

    private void put(int texel, float a, float b, float c, float d) {
        int at = texel * 16;
        data.putFloat(at, a).putFloat(at + 4, b).putFloat(at + 8, c).putFloat(at + 12, d);
    }

    /** The texture the chains read as {@code awaken:lens_frame}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens frame", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA32_FLOAT, WIDTH, 1, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            ByteBuffer zero = MemoryUtil.memCalloc(WIDTH * 16);
            device.createCommandEncoder().writeToTexture(texture, zero, 0, 0, 0, 0, WIDTH, 1);
            MemoryUtil.memFree(zero);
        }
    }
}
