package dev.awaken.living.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.awaken.Awaken;
import dev.awaken.living.pulse.StormCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The Rolling Storm's cell where Lens 2 doesn't draw it (Lens off or Swift, or a shaderpack; SPEC §21.6): the same
 * cumulonimbus as Lens's, built of solid 8-block cloud cells in the game's own cloud style (opaque and face-shaded like
 * vanilla's clouds, so nothing translucent overlaps or sorts), each cell lit by how much of the cloud lies between it
 * and the sun, cold and dark at its base and warm on its sunward turrets at dusk, lit from inside by its lightning. Its
 * rain hangs as fixed curtains (world-anchored sheets, never turned to the camera) in the game's own fog, darker than the
 * sky. The cells are built off the render thread once per storm (and again only while it builds or rains itself out);
 * each frame sends only the outer faces turned to the camera, and allocates nothing.
 */
final class StormFallback {
    private StormFallback() {}

    /** Opaque coloured quads, depth-written, unfogged (a storm stands over the horizon like the game's clouds). */
    static final RenderPipeline CELLS = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Awaken.id("pipeline/storm_cells"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();
    private static final Identifier RAIN = Awaken.id("textures/living/storm_rain.png");
    private static RenderType cells, rain;
    private static int ok = -1;

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterRenderPipelinesEvent.class, e -> e.registerOptionalPipeline(CELLS));
    }

    private static boolean ready() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(CELLS) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[living] the storm's cloud cells pipeline didn't build; the fallback storm draws no cloud");
        }
        if (ok == 0) return false;
        if (cells == null) {
            cells = RenderType.create("awaken_storm_cells", RenderSetup.builder(CELLS).createRenderSetup());
            rain = RenderType.create("awaken_storm_rain", RenderSetup.builder(RenderPipelines.WEATHER)
                    .withTexture("Sampler0", RAIN, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
                    .useLightmap().setOitPipelines(RenderPipelines.OIT_WEATHER).sortOnUpload().createRenderSetup());
        }
        return true;
    }

    // ---- the shape (lens_storm.glsl's, on the CPU) -------------------------------------------------------------------

    static final int CELL = 8, NX = 96, NY = 45, NZ = 96;
    static final double X0 = -NX * CELL / 2.0, Y0 = -CELL, Z0 = -NZ * CELL / 2.0;
    private static final double R = 95, H = StormCell.HEIGHT, DOWN = 340, UP = 70, SIDE = 185, SHELF = 125;
    private static final double[][] FLANK = {{-118, 26, 48, 170}, {-168, 48, 40, 118}, {-210, 66, 32, 78}};
    private static byte[] noise;   // lens_cloud_noise.png, 256x256 RGBA

    private static float hash(float x, float y, float z) {
        x = fract(x * 0.3183099F + 0.1F) * 17F;
        y = fract(y * 0.3183099F + 0.1F) * 17F;
        z = fract(z * 0.3183099F + 0.1F) * 17F;
        return fract(x * y * z * (x + y + z));
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /** lens_storm.glsl's stormPuffs: 1 - the distance to the nearest of 8 jittered points (round puffs, sharp creases). */
    static float puffs(double px, double py, double pz) {
        double qx = px - 0.5, qy = py - 0.5, qz = pz - 0.5;
        double ix = Math.floor(qx), iy = Math.floor(qy), iz = Math.floor(qz);
        double fx = qx - ix, fy = qy - iy, fz = qz - iz;
        double best = 9;
        for (int k = 0; k < 8; k++) {
            int ox = k & 1, oy = (k >> 1) & 1, oz = (k >> 2) & 1;
            float h = hash((float) (ix + ox), (float) (iy + oy), (float) (iz + oz));
            double dx = ox + 0.25 + 0.5 * h - fx, dy = oy + 0.25 + 0.5 * fract(h * 7.13F) - fy, dz = oz + 0.25 + 0.5 * fract(h * 51.7F) - fz;
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return (float) (1 - Math.sqrt(best));
    }

    /** The noise texture's channel {@code ch} at (u, v) in tiles, bilinear and wrapping, 0..1. */
    static float tex(double u, double v, int ch) {
        if (noise == null) return 0.5F;
        double x = (u - Math.floor(u)) * 256 - 0.5, y = (v - Math.floor(v)) * 256 - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        float a = at(x0, y0, ch), b = at(x0 + 1, y0, ch), c = at(x0, y0 + 1, ch), d = at(x0 + 1, y0 + 1, ch);
        return (float) ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy);
    }

    private static float at(int x, int y, int ch) {
        return (noise[((y & 255) * 256 + (x & 255)) * 4 + ch] & 255) / 255F;
    }

    private static double blob(double dist, double r, double puff) {
        return Mth.clamp((r * (0.66 + 0.5 * puff) - dist) / 4.0, 0, 1);
    }

    private static double smooth(double a, double b, double x) {
        double t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** The cell's density at (qx, qy, qz) relative to its centre on the base, downwind (wx, wz): one octave of puffs. */
    static double density(double qx, double qy, double qz, double wx, double wz) {
        double h = qy;
        if (h < -6 || h > H + 46) return 0;
        double along = qx * wx + qz * wz, across = -qx * wz + qz * wx;
        double hn = Mth.clamp(h / H, 0, 1);
        double puff = puffs(qx / 50, qy / 50, qz / 50) * 0.85 + 0.09;
        double base = smooth(-1, 1.5, h + 1.5 * (puff - 0.6));
        double d = 0;
        if (h < 40) {
            // the shelf: a flat dark base wider than the tower, its rim ragged
            double rim = SHELF * (0.82 + 0.36 * tex(qx / 300, qz / 300, 1)) + 14 * (puff - 0.6), sx = along - 8;
            double rd = Math.sqrt(sx * sx + across * across) / Math.max(rim, 1), deep = 32 - 26 * smooth(0.55, 1, rd);
            d = Mth.clamp((1 - rd) * rim / 5, 0, 1) * base * (1 - smooth(deep - 12, deep, h + 12 * (puff - 0.55)));
        }
        if (h < H - 28) {
            double sr = tex(h / 900 + 0.13, 0.37, 0), sg = tex(h / 900 + 0.13, 0.37, 1), sb = tex(h / 900 + 0.13, 0.37, 2);
            double lean = hn * hn * 35;
            double bx = along - lean - 70 * (sr - 0.5), bz = across - 70 * (sg - 0.5);
            double lobes = 0.75 + 0.5 * tex(qx / 420 + h / 900, qz / 420, 0);
            double r = R * (1 - 0.32 * Math.pow(hn, 1.4)) * (0.8 + 0.4 * sb) * lobes;
            d = Math.max(d, blob(Math.sqrt(bx * bx + bz * bz), r, puff) * base * (1 - smooth(H - 30, H, h)));
            for (double[] f : FLANK) {
                double dx = along - f[0], dz = across - f[1], dy = Math.max(h - (f[3] - f[2]), 0);
                d = Math.max(d, blob(Math.sqrt(dx * dx + dy * dy + dz * dz), f[2], puff) * base);
            }
        }
        if (h > H - 72) {
            double ox = along + 10, oy = Math.max(h - H + 40, 0) * 1.3;
            d = Math.max(d, blob(Math.sqrt(ox * ox + across * across + oy * oy), 70, puff) * smooth(H - 60, H - 40, h));
            double ax = along / (along > 0 ? DOWN : UP), ay = across / SIDE;
            double ra = Math.sqrt(ax * ax + ay * ay) + 0.16 * (tex(along / 900, across / 140, 3) - 0.5);
            double top = H + 8 - 3 * ra, lo = H - 70 + 58 * Math.pow(Mth.clamp(ra, 0, 1), 0.55);
            double a = Mth.clamp((1 - ra) * 8, 0, 1) * Mth.clamp((h - lo) / 4, 0, 1) * Mth.clamp((top - h) / 2, 0, 1);
            d = Math.max(d, a * smooth(0, 0.25, 1.2 - ra));
        }
        return d;
    }

    // ---- a storm's cells --------------------------------------------------------------------------------------------

    /** One storm's cloud: which cells are solid, when each shows as it builds, and its outer faces. */
    static final class Cloud {
        final byte[] solid = new byte[NX * NY * NZ];
        final byte[] showAt = new byte[NX * NY * NZ];   // 0..255: the strength at which this cell shows
        final float[] light = new float[NX * NY * NZ];
        volatile int[] faces = new int[0];                // cell index << 3 | direction
        int shownLevel = -1;                              // the strength (in 32nds) the faces were built for
        long litAt = Long.MIN_VALUE;
        CompletableFuture<?> work;
    }

    private static final Map<Integer, Cloud> CLOUDS = new HashMap<>();
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    static int index(int x, int y, int z) {
        return (y * NZ + z) * NX + x;
    }

    private static void loadNoise() {
        if (noise != null) return;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(Awaken.id("textures/effect/lens_cloud_noise.png"));
             NativeImage img = NativeImage.read(in)) {
            byte[] out = new byte[256 * 256 * 4];
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    int argb = img.getPixel(x % img.getWidth(), y % img.getHeight()), o = (y * 256 + x) * 4;
                    out[o] = (byte) (argb >> 16);
                    out[o + 1] = (byte) (argb >> 8);
                    out[o + 2] = (byte) argb;
                    out[o + 3] = (byte) (argb >>> 24);
                }
            }
            noise = out;
        } catch (Exception ex) {
            Awaken.LOG.warn("[living] storm noise unreadable; the fallback cloud is plainer", ex);
            noise = new byte[256 * 256 * 4];
        }
    }

    /** Fills the cloud's cells (off the render thread). */
    private static void shape(Cloud c, double wx, double wz, int seed) {
        for (int y = 0; y < NY; y++) {
            for (int z = 0; z < NZ; z++) {
                for (int x = 0; x < NX; x++) {
                    double qx = X0 + (x + 0.5) * CELL, qy = Y0 + (y + 0.5) * CELL, qz = Z0 + (z + 0.5) * CELL;
                    int i = index(x, y, z);
                    if (density(qx, qy, qz, wx, wz) > 0.45) {
                        c.solid[i] = 1;
                        // it builds from the base up (and rains out from the top down), a little unevenly
                        float rise = (float) Mth.clamp(qy / (H + 40), 0, 1);
                        c.showAt[i] = (byte) Math.round(255 * Math.min(1F, 0.04F + 0.7F * rise + 0.25F * hash(x + seed, y, z)));
                    }
                }
            }
        }
    }

    /** Light per solid cell: how much cloud lies toward the sun (eight cells' worth), softened; off the render thread. */
    private static void light(Cloud c, float lx, float ly, float lz) {
        for (int y = 0; y < NY; y++) {
            for (int z = 0; z < NZ; z++) {
                for (int x = 0; x < NX; x++) {
                    int i = index(x, y, z);
                    if (c.solid[i] == 0) continue;
                    float od = 0;
                    for (int s = 1; s <= 8; s++) {
                        int sx = Math.round(x + lx * s), sy = Math.round(y + ly * s), sz = Math.round(z + lz * s);
                        if (sx < 0 || sy < 0 || sz < 0 || sx >= NX || sy >= NY || sz >= NZ) break;
                        od += c.solid[index(sx, sy, sz)];
                    }
                    c.light[i] = (float) Math.exp(-od * 0.45);
                }
            }
        }
        // softened over each cell's neighbours, so light rolls over the turrets instead of flipping cell by cell
        float[] soft = new float[c.light.length];
        for (int y = 1; y < NY - 1; y++) {
            for (int z = 1; z < NZ - 1; z++) {
                for (int x = 1; x < NX - 1; x++) {
                    int i = index(x, y, z);
                    if (c.solid[i] == 0) continue;
                    float sum = 0;
                    int n = 0;
                    for (int[] d : DIRS) {
                        int j = index(x + d[0], y + d[1], z + d[2]);
                        if (c.solid[j] != 0) {
                            sum += c.light[j];
                            n++;
                        }
                    }
                    soft[i] = (c.light[i] * 2 + sum) / (2 + n);
                }
            }
        }
        System.arraycopy(soft, 0, c.light, 0, soft.length);
    }

    /** The outer faces of the cells shown at {@code level} (32nds of full strength), off the render thread. */
    private static int[] faces(Cloud c, int level) {
        int limit = level * 255 / 32, n = 0;
        int[] out = new int[8192];
        for (int y = 0; y < NY; y++) {
            for (int z = 0; z < NZ; z++) {
                for (int x = 0; x < NX; x++) {
                    int i = index(x, y, z);
                    if (!shown(c, i, limit)) continue;
                    for (int d = 0; d < 6; d++) {
                        int ax = x + DIRS[d][0], ay = y + DIRS[d][1], az = z + DIRS[d][2];
                        boolean open = ax < 0 || ay < 0 || az < 0 || ax >= NX || ay >= NY || az >= NZ || !shown(c, index(ax, ay, az), limit);
                        if (!open) continue;
                        if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
                        out[n++] = i << 3 | d;
                    }
                }
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static boolean shown(Cloud c, int i, int limit) {
        return c.solid[i] != 0 && (c.showAt[i] & 255) <= limit;
    }

    // ---- each frame -------------------------------------------------------------------------------------------------

    private static final Vector3f SUN = new Vector3f();

    static void submit(SubmitCustomGeometryEvent e) {
        if (CLOUDS.size() > StormSky.CELLS.size()) CLOUDS.keySet().removeIf(id -> StormSky.CELLS.stream().noneMatch(c -> c.id == id));
        if (StormSky.CELLS.isEmpty() || !ready()) return;
        var state = e.getLevelRenderState();
        if (state.skyRenderState.skybox != DimensionType.Skybox.OVERWORLD) return;
        var cam = state.cameraRenderState.pos;
        float now = LivingClient.ticks + state.worldPartialTicks;
        // the light: toward the sun by day (or the moon), its warmth at dusk, the game's cloud colour for the sky's
        float angle = state.skyRenderState.sunAngle;
        SUN.set(0F, 1F, 0F).rotateX(angle).rotateY((float) (-Math.PI / 2));
        boolean moon = SUN.y < 0F;
        if (moon) SUN.negate();
        var glow = state.skyRenderState.sunriseAndSunsetColor;
        float day = Mth.clamp(SUN.y * 4F + 0.1F, 0F, 1F) * (moon ? 0.25F : 1F) * state.skyRenderState.rainBrightness;
        float warm = moon ? 0F : glow.w();
        float sr = Mth.lerp(warm, 1F, glow.x() * 1.15F) * day, sg = Mth.lerp(warm, 0.97F, glow.y() * 1.05F) * day,
                sb = Mth.lerp(warm, 0.92F, glow.z()) * day;
        // the sky's light on it: the game's cloud colour, mostly its brightness (its sunset hue is the sun's to give, on the
        // faces turned to it; on every face it read as a terracotta rock)
        int cc = state.cloudColor;
        float cr = ((cc >> 16) & 255) / 255F, cg = ((cc >> 8) & 255) / 255F, cb = (cc & 255) / 255F, lum = 0.3F * cr + 0.59F * cg + 0.11F * cb;
        float ar = Mth.lerp(0.3F, lum, cr) * 0.97F, ag = Mth.lerp(0.3F, lum, cg) * 0.99F, ab = Mth.lerp(0.3F, lum, cb) * 1.04F;
        var sky = state.skyRenderState.skyColor;
        float hr = sky.x(), hg = sky.y(), hb = sky.z();
        loadNoise();
        for (StormSky.Cell s : StormSky.CELLS) {
            if (!StormSky.place(s, now) || s.strength <= 0.001F) continue;
            Cloud cloud = CLOUDS.computeIfAbsent(s.id, id -> new Cloud());
            update(cloud, s);
            int[] faces = cloud.faces;
            if (faces.length == 0) continue;
            float flash = StormSky.flash(s, now);
            double ox = s.x - cam.x, oy = s.baseY - cam.y, oz = s.z - cam.z;
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), cells, (pose, vc) -> guard(() ->
                    cells(vc, cloud, faces, ox, oy, oz, s, flash, cam.x, cam.y, cam.z, sr, sg, sb, ar, ag, ab, hr, hg, hb)));
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), rain, (pose, vc) -> guard(() ->
                    rain(vc, s, ox, oy, oz, now, flash, ar, ag, ab)));
        }
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[living] storm fallback failed", ex);
        }
    }

    /** Starts (off-thread) whatever the cloud needs next: its shape, new faces as it builds or fades, fresh light. */
    private static void update(Cloud cloud, StormSky.Cell s) {
        if (cloud.work != null && !cloud.work.isDone()) return;
        int level = Math.round(s.strength * 32F);
        long tick = LivingClient.ticks;
        boolean relight = tick - cloud.litAt > 100;
        if (level == cloud.shownLevel && !relight) return;
        float lx = SUN.x, ly = SUN.y, lz = SUN.z;
        boolean first = cloud.shownLevel < 0;
        double wx = s.windX, wz = s.windZ;
        int seed = s.id * 31;
        cloud.litAt = tick;
        cloud.work = CompletableFuture.runAsync(() -> {
            if (first) shape(cloud, wx, wz, seed);
            light(cloud, lx, ly, lz);
            if (level != cloud.shownLevel) {
                cloud.faces = faces(cloud, level);
                cloud.shownLevel = level;
            }
        }, Util.backgroundExecutor());
    }

    private static void cells(VertexConsumer vc, Cloud c, int[] faces, double ox, double oy, double oz, StormSky.Cell s, float flash,
                              double camX, double camY, double camZ, float sr, float sg, float sb, float ar, float ag, float ab,
                              float hr, float hg, float hb) {
        // the camera in the cloud's own cells, to keep only faces turned toward it
        double cx = (camX - s.x - X0) / CELL, cy = (camY - s.baseY - Y0) / CELL, cz = (camZ - s.z - Z0) / CELL;
        double fx = (s.fx - s.x - X0) / CELL, fy = (s.fy - s.baseY - Y0) / CELL, fz = (s.fz - s.z - Z0) / CELL;
        for (int f : faces) {
            int i = f >>> 3, d = f & 7;
            int x = i % NX, z = (i / NX) % NZ, y = i / (NX * NZ);
            // turned away from the camera: never seen
            boolean toward = switch (d) {
                case 0 -> cx > x + 1;
                case 1 -> cx < x;
                case 2 -> cy > y + 1;
                case 3 -> cy < y;
                case 4 -> cz > z + 1;
                default -> cz < z;
            };
            if (!toward) continue;
            float hn = Mth.clamp((y + 0.5F) / NY, 0F, 1F);
            // the sky's light: dim and cold at the base, full at the tops; faces shaded as the game's clouds are
            float shade = switch (d) {
                case 2 -> 1F;
                case 3 -> 0.62F;
                case 0, 1 -> 0.8F;
                default -> 0.9F;
            };
            float ao = (0.3F + 0.62F * (float) Math.pow(hn, 0.8)) * shade;
            float bruise = 1F - hn;
            float r = ar * ao * (1F - 0.2F * bruise), g = ag * ao * (1F - 0.18F * bruise), b = ab * ao;
            // the sun's, on faces turned to it, as much as gets through the cloud
            float n = Math.max(0F, DIRS[d][0] * SUN.x + DIRS[d][1] * SUN.y + DIRS[d][2] * SUN.z);
            float sun = c.light[i] * (0.25F + 0.75F * n) * 0.85F;
            r += sr * sun;
            g += sg * sun;
            b += sb * sun;
            if (flash > 0F) {
                double ddx = x + 0.5 - fx, ddy = y + 0.5 - fy, ddz = z + 0.5 - fz;
                float lit = flash * 1.7F * (float) Math.exp(-Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) * CELL / 42.0);
                r += 0.86F * lit;
                g += 0.9F * lit;
                b += lit;
            }
            // far off it takes on the sky's colour, like the game's clouds in haze
            double wx = ox + X0 + (x + 0.5) * CELL, wy = oy + Y0 + (y + 0.5) * CELL, wz = oz + Z0 + (z + 0.5) * CELL;
            float haze = (float) (1 - Math.exp(-Math.sqrt(wx * wx + wy * wy + wz * wz) / 1400.0)) * 0.6F;
            int col = 0xFF000000 | channel(Mth.lerp(haze, r, hr)) << 16 | channel(Mth.lerp(haze, g, hg)) << 8 | channel(Mth.lerp(haze, b, hb));
            quad(vc, ox, oy, oz, x, y, z, d, col);
        }
    }

    private static int channel(float v) {
        return Mth.clamp(Math.round(v * 255F), 0, 255);
    }

    /** One cell face, camera-relative. */
    private static void quad(VertexConsumer vc, double ox, double oy, double oz, int x, int y, int z, int d, int col) {
        float x0 = (float) (ox + X0 + x * CELL), y0 = (float) (oy + Y0 + y * CELL), z0 = (float) (oz + Z0 + z * CELL);
        float x1 = x0 + CELL, y1 = y0 + CELL, z1 = z0 + CELL;
        switch (d) {
            case 0 -> face(vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, col);
            case 1 -> face(vc, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, col);
            case 2 -> face(vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, col);
            case 3 -> face(vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, col);
            case 4 -> face(vc, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, col);
            default -> face(vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, col);
        }
    }

    private static void face(VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, int col) {
        vc.addVertex(ax, ay, az).setColor(col);
        vc.addVertex(bx, by, bz).setColor(col);
        vc.addVertex(cx, cy, cz).setColor(col);
        vc.addVertex(dx, dy, dz).setColor(col);
    }

    // ---- its rain -----------------------------------------------------------------------------------------------------

    private static final int SHEETS = 9, SEGMENTS = 8;

    /**
     * Rain from the base to the ground: curved sheets fixed in the world around the rain core (arcs at set radii and
     * angles, so from any side several overlap into a veil), slanting downwind as they fall, the texture scrolling down,
     * faded at their ends, top and foot. Dark grey, as rain is against a storm's light.
     */
    private static void rain(VertexConsumer vc, StormSky.Cell s, double ox, double oy, double oz, float now, float flash, float ar, float ag, float ab) {
        double ground = s.ground - 2 - s.baseY, top = 4;   // relative to the base
        double rx = s.windX * StormCell.RAIN_AHEAD, rz = s.windZ * StormCell.RAIN_AHEAD;
        double slant = 0.3 * -ground;                       // how far downwind the rain has gone by the ground
        float lift = 1F + flash * 1.5F;
        int col = channel(Mth.clamp(ar * 0.42F * lift, 0F, 1F)) << 16 | channel(Mth.clamp(ag * 0.45F * lift, 0F, 1F)) << 8
                | channel(Mth.clamp(ab * 0.5F * lift, 0F, 1F));
        float alpha = 0.55F * s.strength;
        // the texture slides down the sheets as the rain falls (v grows downward, so the offset shrinks over time), at
        // about 19 blocks a second: from a few hundred blocks off, faster reads as streaming water
        float scroll = 1F - (now * 0.02F) % 1F;
        for (int k = 0; k < SHEETS; k++) {
            float u0 = k * 0.37F;
            double radius = StormCell.RAIN_RADIUS * (0.25 + 0.7 * fract(k * 0.618F + 0.11F));
            double start = k * 2.39996, span = 1.1 + 0.9 * fract(k * 0.41F + 0.3F);
            for (int j = 0; j < SEGMENTS; j++) {
                double a0 = start + span * j / SEGMENTS, a1 = start + span * (j + 1) / SEGMENTS;
                float e0 = edge(j / (float) SEGMENTS), e1 = edge((j + 1) / (float) SEGMENTS);
                double x0 = rx + Math.cos(a0) * radius, z0 = rz + Math.sin(a0) * radius, x1 = rx + Math.cos(a1) * radius, z1 = rz + Math.sin(a1) * radius;
                double seg = radius * span / SEGMENTS / 24.0;
                float ua = u0 + (float) (j * seg), ub = u0 + (float) ((j + 1) * seg);
                float vTop = scroll, vFoot = scroll + (float) ((top - ground) / 48.0);
                int c0 = (int) (alpha * e0 * 255) << 24 | col, c1 = (int) (alpha * e1 * 255) << 24 | col;
                int f0 = (int) (alpha * e0 * 140) << 24 | col, f1 = (int) (alpha * e1 * 140) << 24 | col;
                // top edge (soft against the base), foot (thinner where it meets the ground)
                vc.addVertex((float) (ox + x0), (float) (oy + top), (float) (oz + z0)).setUv(ua, vTop).setColor(c0 & 0x00FFFFFF).setLight(0xF000F0);
                vc.addVertex((float) (ox + x1), (float) (oy + top), (float) (oz + z1)).setUv(ub, vTop).setColor(c1 & 0x00FFFFFF).setLight(0xF000F0);
                vc.addVertex((float) (ox + x1 + s.windX * slant * 0.15), (float) (oy + top - 14), (float) (oz + z1 + s.windZ * slant * 0.15))
                        .setUv(ub, vTop + 14 / 48F).setColor(c1).setLight(0xF000F0);
                vc.addVertex((float) (ox + x0 + s.windX * slant * 0.15), (float) (oy + top - 14), (float) (oz + z0 + s.windZ * slant * 0.15))
                        .setUv(ua, vTop + 14 / 48F).setColor(c0).setLight(0xF000F0);
                vc.addVertex((float) (ox + x0 + s.windX * slant * 0.15), (float) (oy + top - 14), (float) (oz + z0 + s.windZ * slant * 0.15))
                        .setUv(ua, vTop + 14 / 48F).setColor(c0).setLight(0xF000F0);
                vc.addVertex((float) (ox + x1 + s.windX * slant * 0.15), (float) (oy + top - 14), (float) (oz + z1 + s.windZ * slant * 0.15))
                        .setUv(ub, vTop + 14 / 48F).setColor(c1).setLight(0xF000F0);
                vc.addVertex((float) (ox + x1 + s.windX * slant), (float) (oy + ground), (float) (oz + z1 + s.windZ * slant))
                        .setUv(ub, vFoot).setColor(f1).setLight(0xF000F0);
                vc.addVertex((float) (ox + x0 + s.windX * slant), (float) (oy + ground), (float) (oz + z0 + s.windZ * slant))
                        .setUv(ua, vFoot).setColor(f0).setLight(0xF000F0);
            }
        }
    }

    /** A sheet fades in and out along its arc. */
    private static float edge(float t) {
        return (float) (smooth(0, 0.3, t) * (1 - smooth(0.7, 1, t)));
    }

    static void reset() {
        CLOUDS.clear();
    }
}
