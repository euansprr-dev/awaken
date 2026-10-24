package dev.awaken.farsight.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.awaken.Awaken;
import dev.awaken.compat.FarView;
import dev.awaken.compat.client.FarTerrain;
import dev.awaken.farsight.FarPalette;
import dev.awaken.farsight.FarStore;
import dev.awaken.farsight.FarWorkers;
import dev.awaken.farsight.Rings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Far Sight on the client (SPEC §16): Awaken's own far terrain, rings of predicted heights drawn right after the sky.
 * Stage 0 predicts from the integrated server's generator (singleplayer); a client on a remote server draws nothing
 * yet. Off unless turned on (autopilot {@code farsight on}, or {@code -Dawaken.farsight=true}), and never while
 * Distant Horizons is installed, since both would draw far terrain and report it to Lens.
 *
 * <p>Each frame, in {@link RenderLevelStageEvent.AfterSky}: place the windows, upload what the workers finished, bake
 * the changed rings' shading, plan the visible pieces, draw, and report the depth to Lens. While it draws, vanilla's
 * render-distance fog moves out to Far Sight's horizon, and the clear-weather atmospheric fog (1024 blocks by default)
 * stretches with it, or nothing past a kilometre would show.
 */
public final class FarSightClient {
    private FarSightClient() {}

    /** Where far terrain starts: this far inside the render distance it dithers in, over the same distance. */
    static final int CLIP_BAND = 32;
    private static final int UPLOADS_PER_FRAME = 48, CHANGES_PER_FRAME = 1024;

    private static boolean enabled = Boolean.getBoolean("awaken.farsight");
    private static FarField field;
    private static FarRenderer renderer;
    private static FarGpu gpu;
    private static final FarWorkers workers = new FarWorkers(FarWorkers.defaultThreads());
    private static ServerLevel bound;
    private static int boundRd;
    private static long joinNanos, bindNanos, lastDrawNanos;
    private static int ringsDone;
    private static boolean horizonLogged;
    private static float fogStart, fogEnd;
    private static final Vector3f sun = new Vector3f();
    // the render thread's time in afterSky, for status
    private static long cpuNanos;
    private static int cpuFrames;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterRenderPipelinesEvent.class, FarPipelines::onRegister);
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("farsight"),
                (ResourceManagerReloadListener) manager -> {
                    FarPipelines.recheck();
                    FarPalette.clear();
                }));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterSky.class, FarSightClient::afterSky);
        // before Lens (LOWEST), which reads vanilla's render-distance end through FarTerrain
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ViewportEvent.RenderFog.class, FarSightClient::fog);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> joinNanos = System.nanoTime());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> unbind());
    }

    /** Whether Far Sight drew in the last quarter second. */
    public static boolean drawing() {
        return enabled && lastDrawNanos != 0 && System.nanoTime() - lastDrawNanos < 250_000_000L;
    }

    private static boolean allowed() {
        return enabled && !ModList.get().isLoaded("distanthorizons");
    }

    private static void unbind() {
        FarStore.collecting = false;
        workers.clear();
        if (field != null) field.reset();
        bound = null;
        lastDrawNanos = 0;
    }

    private static void afterSky(RenderLevelStageEvent.AfterSky e) {
        if (!allowed()) {
            if (bound != null) unbind();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.level == null || server == null || !FarPipelines.ok()) return;
        ServerLevel level = server.getLevel(mc.level.dimension());
        if (level == null || !FarView.allowed(mc.level.dimension().identifier().toString())
                || mc.level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD) {
            if (bound != null) unbind();
            return;
        }
        long t0 = System.nanoTime();
        if (field == null) {
            field = new FarField();
            renderer = new FarRenderer();
            gpu = new FragmentFarGpu();
            Awaken.LOG.info("[farsight] ready: {} rings of {}x{} cells, GPU work by {}", Rings.RINGS, Rings.SIZE, Rings.SIZE, gpu.name());
        }
        int rd = mc.options.getEffectiveRenderDistance();
        if (level != bound || rd != boundRd) {
            unbind();
            bound = level;
            boundRd = rd;
            bindNanos = System.nanoTime();
            ringsDone = 0;
            horizonLogged = false;
        }
        // the integrated server sums real chunks into the generated layer while Far Sight draws
        FarStore.collecting = true;
        FarStore store = FarStore.of(level);
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        float clipStart = Math.max(0, rd * 16 - CLIP_BAND);
        // coarsest first: a window's first patches queue ahead of the finer rings'
        int far = Rings.RINGS - 1;
        long farWindow = field.valid[far] ? Rings.key(field.winX[far], field.winZ[far]) : Long.MIN_VALUE;
        for (int r = far; r >= 0; r--) field.place(r, cam.x, cam.z, clipStart, level, store, workers);
        for (int i = 0; i < CHANGES_PER_FRAME; i++) {
            long chunk = store.pollChanged();
            if (chunk == Long.MIN_VALUE) break;
            field.chunkChanged(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), cam.x, cam.z, level, workers);
        }
        if (farWindow != Long.MIN_VALUE && farWindow != Rings.key(field.winX[far], field.winZ[far])) {
            // a jump (or 2 km of travel): time the new horizon from here
            bindNanos = System.nanoTime();
            joinNanos = 0;
            ringsDone = 0;
            horizonLogged = false;
        }
        for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
            FarWorkers.Patch p = workers.poll();
            if (p == null) break;
            if (field.accept(p) && field.again) field.regenerate(p.ring, p.px, p.pz, cam.x, cam.z, level, workers);
            workers.recycle(p);
        }
        logProgress();
        renderer.timeStart();
        if (field.dirty != 0) {
            gpu.bake(field, field.dirty);
            field.dirty = 0;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        var camera = mc.gameRenderer.mainCamera();
        float fov = Math.max(camera.getFov(), mc.options.fov().get()) + 10f;
        renderer.plan(field, cam.x, cam.y, cam.z, e.getModelViewMatrix(), fov, (float) main.width / Math.max(1, main.height), clipStart,
                mc.level.getMinY(), mc.level.getMaxY());
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial) * (float) (Math.PI / 180.0);
        // where SkyRenderer puts the sun; below the horizon the moon (opposite) lights the slopes, the lightmap the rest
        sun.set(0f, 1f, 0f).rotateX(angle).rotateY((float) (-Math.PI / 2));
        if (sun.y < 0f) sun.negate();
        fogEnd = horizon();
        fogStart = fogEnd * 0.5f;
        if (RenderSystem.getProjectionMatrixBuffer() == null || RenderSystem.getShaderFog() == null) {
            renderer.timeEnd();
            return;
        }
        renderer.draw(field, main, e.getModelViewMatrix(), cam.y, sun, fogStart, fogEnd, clipStart, CLIP_BAND, level.getSeaLevel(),
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(), mc.gameRenderer.levelLightmap());
        renderer.timeEnd();
        FarTerrain.report(renderer.depthTexture(), FarRenderer.NEAR, FarRenderer.FAR, true, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        lastDrawNanos = System.nanoTime();
        cpuNanos += lastDrawNanos - t0;
        cpuFrames++;
    }

    /** How far Far Sight is fogged out: the coarsest ring's reach, less the most its window can lag the camera. */
    static float horizon() {
        return Rings.reach(Rings.RINGS - 1) - Rings.patchBlocks(Rings.RINGS - 1) * 0.75f - 256f;
    }

    private static void logProgress() {
        if (ringsDone == (1 << Rings.RINGS) - 1) return;
        long now = System.nanoTime();
        for (int r = 0; r < Rings.RINGS; r++) {
            if ((ringsDone & 1 << r) != 0 || !field.valid[r] || field.missing[r] > 0) continue;
            ringsDone |= 1 << r;
            Awaken.LOG.info("[farsight] ring {} ({} m) ready {} ms after the first frame, {} ms after joining", r, Rings.reach(r),
                    (now - bindNanos) / 1_000_000, joinNanos == 0 ? -1 : (now - joinNanos) / 1_000_000);
        }
        int far = (1 << Rings.RINGS) - (1 << Rings.EXACT_RINGS);
        if (!horizonLogged && (ringsDone & far) == far) {
            horizonLogged = true;
            Awaken.LOG.info("[farsight] horizon (rings {}-{}, to {} km) ready {} ms after the first frame, {} ms after joining",
                    Rings.EXACT_RINGS, Rings.RINGS - 1, Rings.reach(Rings.RINGS - 1) / 1000, (now - bindNanos) / 1_000_000,
                    joinNanos == 0 ? -1 : (now - joinNanos) / 1_000_000);
        }
        if (ringsDone == (1 << Rings.RINGS) - 1) Awaken.LOG.info("[farsight] all rings ready {} ms after the first frame; {}", (now - bindNanos) / 1_000_000, status());
    }

    private static void fog(ViewportEvent.RenderFog e) {
        if (!drawing()) return;
        FogData fog = e.getFogData();
        FarTerrain.renderDistanceFog(fog.renderDistanceEnd);
        fog.renderDistanceStart = fogStart;
        fog.renderDistanceEnd = fogEnd;
        // clear-weather haze stretches out to Far Sight's horizon; rain brings most of it back, thick fog (a boss, the
        // Veil, a gate's weather) is never thinned
        if (e.getEnvironment() instanceof AtmosphericFogEnvironment && fog.environmentalEnd >= 200f) {
            Minecraft mc = Minecraft.getInstance();
            float rain = mc.level == null ? 0f : mc.level.getRainLevel((float) e.getPartialTick());
            float k = 1f + (fogEnd * 1.6f / 1024f - 1f) * (1f - rain * 0.85f);
            fog.environmentalStart *= k;
            fog.environmentalEnd *= k;
        }
    }

    /** Autopilot: {@code farsight on|off|status}. */
    public static String autopilot(String arg) {
        switch (arg.trim()) {
            case "on" -> {
                enabled = true;
                if (ModList.get().isLoaded("distanthorizons")) return "on, but Distant Horizons is installed: Far Sight stays off";
            }
            case "off" -> {
                enabled = false;
                unbind();
            }
            case "status" -> {
            }
            default -> {
                return "unknown: " + arg.trim() + " (on|off|status)";
            }
        }
        return status();
    }

    static String status() {
        if (field == null) return enabled ? "on, not drawn yet" : "off";
        StringBuilder rings = new StringBuilder();
        for (int r = 0; r < Rings.RINGS; r++) rings.append(r == 0 ? "" : ",").append(field.missing[r]);
        long ec = workers.estimateColumns.get(), xc = workers.exactColumns.get();
        double cpu = cpuFrames == 0 ? -1 : cpuNanos / 1e6 / cpuFrames;
        cpuNanos = 0;
        cpuFrames = 0;
        return String.format(Locale.ROOT,
                "%s gpu_ms=%.3f cpu_ms=%.3f missing=[%s] queued=%d pieces=%d cells=%d estimate=%.1fus/column exact=%.1fus/column (%d/%d columns) vram=%dMB",
                drawing() ? "drawing" : enabled ? "on" : "off", renderer.gpuMillis(), cpu, rings, workers.queued(), renderer.pieces, renderer.cells,
                ec == 0 ? 0.0 : workers.estimateNanos.get() / 1000.0 / ec, xc == 0 ? 0.0 : workers.exactNanos.get() / 1000.0 / xc, ec, xc,
                (field.memoryBytes() + renderer.memoryBytes()) >> 20);
    }
}
