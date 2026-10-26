package dev.awaken.farsight.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.compat.FarView;
import dev.awaken.compat.client.FarTerrain;
import dev.awaken.farsight.FarCity;
import dev.awaken.farsight.FarLandmarks;
import dev.awaken.farsight.FarPalette;
import dev.awaken.farsight.FarStore;
import dev.awaken.farsight.FarWorkers;
import dev.awaken.farsight.Rings;
import dev.awaken.farsight.voxel.VoxelCapture;
import dev.awaken.farsight.voxel.VoxelMesh;
import dev.awaken.farsight.voxel.VoxelSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Util;
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
 * Far Sight on the client (SPEC §20): Awaken's own far terrain, rings of predicted heights drawn right after the sky.
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
    /** The share of near chunks meshed for the view to count as warm. */
    static final float WARM_SHOWN = 0.97f;
    /** How far under sea level the lowest ground in view is taken to lie (a sea floor), for what the chunks cover. */
    static final int SEA_FLOOR = 40;
    /** The render distance's outer band where far water may lie under the real water (farsight_terrain.fsh's 80). */
    static final float SEA_BAND = 80f;
    /** Valley cloud: its floor above sea level, its depth (blocks) and density per block (SPEC §20). */
    static final float MIST_ABOVE_SEA = 40f, MIST_DEPTH = 46f, MIST_DENSITY = 0.014f;
    private static final int UPLOADS_PER_FRAME = 48, CHANGES_PER_FRAME = 1024;

    /** Autopilot's override (null: the client setting decides). {@code -Dawaken.farsight=true} forces it on. */
    private static Boolean forced = Boolean.getBoolean("awaken.farsight") ? Boolean.TRUE : null;
    private static FarField field;
    private static FarRenderer renderer;
    private static FarGpu gpu;
    /** City Sight (Far Sight 3): the real chunks' blocks where Sodium doesn't draw them. */
    private static VoxelLayer voxels;
    /** Snapshots taken from the server per frame, and the bytes of finished meshes uploaded per frame. */
    private static final int SNAPSHOTS_PER_FRAME = 48, VOXEL_UPLOAD_BYTES = 6 << 20;
    private static final FarWorkers workers = new FarWorkers(FarWorkers.defaultThreads(), FarWorkers.burstThreads());
    private static ServerLevel bound;
    /** The level City Sight's meshes belong to (kept across an off and on in it). */
    private static ServerLevel voxelLevel;
    private static int boundRd, boundRings = Rings.RINGS;
    private static long joinNanos, bindNanos, lastDrawNanos;
    private static int ringsDone;
    private static boolean horizonLogged;
    private static float fogStart, fogEnd;
    private static final Vector3f sun = new Vector3f(), sunColour = new Vector3f();
    private static int bakeTurn;
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
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            unbind();
            if (voxels != null) voxels.clear();
            voxelLevel = null;
        });
    }

    /**
     * Whether the view is warm (the opening's flight waits behind its card for it, SPEC §16.4): every far ring's patches
     * in, and nearly all the real chunks well inside the render distance loaded and meshed, so nothing shows a hole, a
     * seam or the land under a city that isn't there yet. True at once when Far Sight doesn't draw.
     */
    public static boolean warm() {
        if (!drawing() || field == null || renderer == null) return true;
        for (int r = 0; r < rings(); r++) if (field.missing[r] > 0) return false;
        // City Sight: the whole city meshed and up (the flight shows all of it). Then the real chunks don't matter: the
        // flight's camera is far from the player, Sodium doesn't build them for it, and City Sight draws them anyway
        // (waiting on them held the card its full 20 s every time)
        if (VoxelCapture.wanted) return VoxelCapture.idle() && voxels.inFlight.get() == 0 && voxels.finished.isEmpty();
        return renderer.shownShare >= WARM_SHOWN;
    }

    /** Whether Far Sight drew in the last quarter second. */
    public static boolean drawing() {
        return enabled() && lastDrawNanos != 0 && System.nanoTime() - lastDrawNanos < 250_000_000L;
    }

    private static boolean enabled() {
        if (forced != null) return forced;
        return AwakenConfig.Client.SPEC.isLoaded() && AwakenConfig.Client.FAR_SIGHT.get() != AwakenConfig.Client.FarSight.OFF;
    }

    /** How many rings draw: all seven (to 16 km), or five (to 4 km) on the NEAR setting. */
    static int rings() {
        return forced == null && AwakenConfig.Client.SPEC.isLoaded() && AwakenConfig.Client.FAR_SIGHT.get() == AwakenConfig.Client.FarSight.NEAR
                ? 5 : Rings.RINGS;
    }

    private static boolean allowed() {
        return enabled() && !ModList.get().isLoaded("distanthorizons");
    }

    /**
     * The player's chunk fade-in, while Far Sight holds it off (-1: not held). New chunks fade in from the fog colour,
     * a white veil sweeping over the city as it loads (Euan, 2026-10-26); with Far Sight there is nothing to fade from,
     * since the far view already shows those blocks, so the fade is off while Far Sight draws and the player's own
     * value comes back when it stops.
     */
    private static double heldFade = -1;
    private static long lastCamNanos;
    private static double lastCamX, lastCamZ, velX, velZ;
    private static int tendTurn;

    private static void holdFade(boolean hold) {
        var option = Minecraft.getInstance().options.chunkSectionFadeInTime();
        if (hold) {
            if (option.get() > 0.0) {
                if (heldFade < 0) heldFade = option.get();
                option.set(0.0);
            }
        } else if (heldFade >= 0) {
            option.set(heldFade);
            heldFade = -1;
        }
    }

    private static void unbind() {
        holdFade(false);
        FarStore.collecting = false;
        VoxelCapture.wanted = false;
        // (the server may not tick between this and a rebind: start its capture over from here)
        VoxelCapture.reset();
        FarCity.underCitySight = false;
        // City Sight's meshes stay while the world does: switched back on, the capture starts over and every chunk
        // whose blocks are unchanged hashes the same and is skipped (Euan saw the city rebuild after an on/off)
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
        pace(t0);
        if (field == null) {
            field = new FarField();
            renderer = new FarRenderer();
            voxels = new VoxelLayer(level.getMinY());
            gpu = new FragmentFarGpu();
            Awaken.LOG.info("[farsight] ready: {} rings of {}x{} cells, GPU work by {}", Rings.RINGS, Rings.SIZE, Rings.SIZE, gpu.name());
        }
        int rd = mc.options.getEffectiveRenderDistance(), shown = rings();
        if (level != bound || rd != boundRd || shown != boundRings) {
            unbind();
            if (level != voxelLevel) {
                voxels.clear();
                renderer.forgetSections();
                voxelLevel = level;
            }
            bound = level;
            boundRd = rd;
            boundRings = shown;
            bindNanos = System.nanoTime();
            ringsDone = 0;
            horizonLogged = false;
            // City Sight draws the overworld's city from its real chunks; the far terrain then carries only its ground
            boolean city = level.dimension() == net.minecraft.world.level.Level.OVERWORLD && dev.awaken.city.City.layout() != null;
            VoxelCapture.wanted = city;
            FarCity.underCitySight = city;
            if (city) voxels.pin(dev.awaken.city.City.layout().chunks);
        }
        holdFade(true);
        // the integrated server sums real chunks into the generated layer while Far Sight draws
        FarStore.collecting = true;
        FarStore store = FarStore.of(level);
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        float clipStart = Math.max(0, rd * 16 - CLIP_BAND);
        // the real chunks are culled in 3D (Sodium: a sphere of the render distance), so from high up they cover less
        // ground than the render distance: horizontally only as far as reaches a sea floor under the camera (16-block
        // steps, so a climb doesn't churn the windows); the shader hides far terrain only where they are drawn
        // the mask keeps a section clear of the render distance: chunks are culled whole, by their sections
        float hidden = Math.max(0f, clipStart - 16f);
        double drop = Math.max(0.0, cam.y - (level.getSeaLevel() - SEA_FLOOR));
        float covered = (float) (Math.floor(Math.sqrt(Math.max(0.0, hidden * (double) hidden - drop * drop)) / 16.0) * 16.0);
        // coarsest first: a window's first patches queue ahead of the finer rings'
        // where the camera is heading: 1.5 s ahead at its smoothed speed, at most 300 blocks (the queue's lead)
        long nowNanos = System.nanoTime();
        if (lastCamNanos != 0) {
            double dt = Math.max(1e-3, (nowNanos - lastCamNanos) / 1e9);
            double vx = (cam.x - lastCamX) / dt, vz = (cam.z - lastCamZ) / dt;
            if (Math.abs(vx) > 2000 || Math.abs(vz) > 2000) vx = vz = 0; // a jump, not a flight
            velX += (vx - velX) * 0.1;
            velZ += (vz - velZ) * 0.1;
        }
        lastCamNanos = nowNanos;
        lastCamX = cam.x;
        lastCamZ = cam.z;
        double lead = Math.min(1.0, 300.0 / Math.max(1e-3, Math.hypot(velX, velZ) * 1.5));
        field.leadX = velX * 1.5 * lead;
        field.leadZ = velZ * 1.5 * lead;
        int far = shown - 1;
        long farWindow = field.valid[far] ? Rings.key(field.winX[far], field.winZ[far]) : Long.MIN_VALUE;
        // ring 0 loads down to the render distance's outer band, where its sea can lie under real water (FarRenderer.rect);
        // the workers take the horizon first, then the nearest patches (FarField.priority)
        float sea = (partsOff & 64) == 0 ? Math.max(0f, covered - SEA_BAND - 32f) : covered;
        field.place(far, cam.x, cam.z, covered, level, store, workers);
        for (int r = 0; r < far; r++) field.place(r, cam.x, cam.z, r == 0 ? sea : covered, level, store, workers);
        // much of the view missing (a new world, a jump, the opening's card, fast flight): every burst worker, unpaced,
        // so the middle rings never stand empty for long (Euan, 2026-10-26: see-through ground outside Carillon)
        int gaps = 0;
        for (int r = 0; r < Rings.RINGS; r++) gaps += field.missing[r];
        cold = gaps > COLD_GAPS;
        workers.active = cold ? workers.burst() : workers.threads();
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
        // City Sight: the server's snapshots to the workers (the city before the nearer far land), their meshes up
        for (int i = 0; i < SNAPSHOTS_PER_FRAME; i++) {
            VoxelSnapshot snap = VoxelCapture.SNAPSHOTS.poll();
            if (snap == null) break;
            VoxelCapture.took();
            int slot = voxels.slot(snap.chunkX(), snap.chunkZ());
            if (slot < 0) continue;
            double dx = snap.chunkX() * 16 + 8 - cam.x, dz = snap.chunkZ() * 16 + 8 - cam.z;
            VoxelLayer layer = voxels;
            layer.inFlight.incrementAndGet();
            workers.submitTask(5.0e5f + (float) Math.sqrt(dx * dx + dz * dz), () -> {
                try {
                    // the same blocks and light as the mesh already up: nothing to do
                    long hash = snap.hash();
                    if (layer.hashes[slot] == hash) return;
                    long t = System.nanoTime();
                    VoxelMesh mesh = layer.mesher.get().mesh(snap, slot);
                    layer.meshNanos.addAndGet(System.nanoTime() - t);
                    layer.meshed.incrementAndGet();
                    if (mesh.quadCount(0) == 0) layer.empty.incrementAndGet();
                    layer.finished.add(mesh);
                    layer.hashes[slot] = hash;
                } finally {
                    layer.inFlight.decrementAndGet();
                }
            });
        }
        voxels.upload(VOXEL_UPLOAD_BYTES);
        if (++tendTurn % 60 == 0) voxels.tend(cam.x, cam.z, VoxelCapture::forget);
        logProgress();
        renderer.timeStart();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var cameraNow = mc.gameRenderer.mainCamera();
        float sunAngle = cameraNow.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick) * (float) (Math.PI / 180.0);
        // where SkyRenderer puts the sun; below the horizon the moon (opposite) lights the slopes
        sun.set(0f, 1f, 0f).rotateX(sunAngle).rotateY((float) (-Math.PI / 2));
        boolean moon = sun.y < 0f;
        if (moon) sun.negate();
        // the changed rings at once, and one more every other frame in turn, so ridge shadows follow the sun (the
        // other frames refresh the chunk mask)
        int bake = field.dirty | ((bakeTurn & 1) == 0 ? 1 << ((bakeTurn >> 1) % Rings.RINGS) : 0);
        bakeTurn++;
        if ((partsOff & 8) == 0) gpu.bake(field, bake, sun, sun.y > 0.01f);
        field.dirty = 0;
        renderer.timeBaked();
        if ((bakeTurn & 1) == 0) renderer.chunks(mc.level, mc.levelRenderer, Util.toMillis(mc.options.chunkSectionFadeInTime().get()), Math.floorDiv((int) Math.floor(cam.x), 16), Math.floorDiv((int) Math.floor(cam.z), 16), rd + 1,
                cam.x, cam.z, rd * 16);
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        var camera = mc.gameRenderer.mainCamera();
        float fov = Math.max(camera.getFov(), mc.options.fov().get()) + 10f;
        renderer.plan(field, cam.x, cam.y, cam.z, e.getModelViewMatrix(), fov, (float) main.width / Math.max(1, main.height), covered, hidden,
                mc.level.getMinY(), mc.level.getMaxY(), level.getSeaLevel());
        renderer.planTrees(field, cam.y, covered, hidden);
        // a block one block away spans this many pixels: City Sight picks each chunk's level by it
        float pixelsAt1 = (float) (main.height / (2.0 * Math.tan(Math.toRadians(Math.max(10f, camera.getFov())) / 2.0)));
        float voxelHaze = 1.5e-4f * (1f + 3f * mc.level.getRainLevel(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
        voxels.plan(renderer, cam.x, cam.y, cam.z, pixelsAt1, rd * 16, (partsOff & 256) == 0, voxelHaze);
        // the far land steps aside exactly where City Sight draws this frame
        voxels.cover();
        if ((partsOff & 2) != 0) renderer.treeDraws = 0;
        // the key light: the sun's strength by its height (a faint moon by night), weaker in rain; warm as it lowers
        float rain = mc.level.getRainLevel(partialTick), thunder = mc.level.getThunderLevel(partialTick);
        float elevation = Math.max(0f, sun.y);
        float strength = (moon ? 0.25f : 1f) * smooth(0.0f, 0.18f, elevation) * (1f - rain * 0.85f) * (1f - thunder * 0.5f);
        float golden = moon ? 0f : 1f - smooth(0.06f, 0.45f, elevation);
        if (moon) sunColour.set(0.70f, 0.80f, 1.00f);
        else sunColour.set(1.00f + 0.30f * golden, 0.98f - 0.12f * golden, 0.94f - 0.36f * golden);
        // the air: at sea level about 25 km of sight, thinning with height (scale height 500), so a ridge at y 250 sees
        // about 40 km; rain thickens it
        float haze = 1.5e-4f * (1f + 3f * rain);
        // valley cloud: lying low in the valleys among the mountains, more of it at dawn and dusk and in rain
        float mistCover = Math.min(1f, 0.34f + 0.26f * golden + 0.35f * rain);
        fogEnd = horizon();
        fogStart = fogEnd * 0.5f;
        if (RenderSystem.getProjectionMatrixBuffer() == null || RenderSystem.getShaderFog() == null) {
            renderer.timeEnd();
            return;
        }
        renderer.draw(field, main, e.getModelViewMatrix(), cam.x, cam.y, cam.z, sun, strength, sunColour, golden, haze, rain,
                level.getSeaLevel() + MIST_ABOVE_SEA, MIST_DEPTH, MIST_DENSITY, mistCover, fogStart, fogEnd,
                hidden, CLIP_BAND, rd * 16, level.getSeaLevel(), RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(), mc.gameRenderer.levelLightmap(),
                FarLandmarks.current(level.dimension()), voxels);
        renderer.timeEnd();
        FarTerrain.report(renderer.depthTexture(), FarRenderer.NEAR, FarRenderer.FAR, true, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        // Far Sight hazes its own kilometres (aerial perspective at real scale): Lens keeps only its near haze over it
        FarTerrain.selfHazed(true);
        lastDrawNanos = System.nanoTime();
        cpuNanos += lastDrawNanos - t0;
        cpuFrames++;
    }

    private static long lastFrameNanos;
    /** Missing patches past which the view counts as cold (about a quarter of one ring). */
    static final int COLD_GAPS = 64;
    private static boolean cold;
    private static float frameMs = 8f;

    /**
     * Paces the workers by the frames: a smoothed frame time over 13 ms (under ~75 fps) rests them after each near patch,
     * up to 12 ms a patch (about half their time) by 18 ms; under it they run flat out.
     */
    private static void pace(long now) {
        if (lastFrameNanos != 0) {
            float ms = Math.min(100f, (now - lastFrameNanos) / 1e6f);
            frameMs += (ms - frameMs) * 0.05f;
        }
        lastFrameNanos = now;
        workers.restMicros = (partsOff & 16) != 0 || cold ? 0 : Math.round(Math.clamp((frameMs - 13f) / 5f, 0f, 1f) * 12_000f);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** How far Far Sight is fogged out: the coarsest ring's reach, less the most its window can lag the camera. */
    static float horizon() {
        int far = boundRings - 1;
        return Rings.reach(far) - Rings.patchBlocks(far) * 0.75f - 256f;
    }

    private static void logProgress() {
        int all = (1 << boundRings) - 1;
        if (ringsDone == all) return;
        long now = System.nanoTime();
        for (int r = 0; r < boundRings; r++) {
            if ((ringsDone & 1 << r) != 0 || !field.valid[r] || field.missing[r] > 0) continue;
            ringsDone |= 1 << r;
            Awaken.LOG.info("[farsight] ring {} ({} m) ready {} ms after the first frame, {} ms after joining", r, Rings.reach(r),
                    (now - bindNanos) / 1_000_000, joinNanos == 0 ? -1 : (now - joinNanos) / 1_000_000);
        }
        int far = all - (1 << 3) + 1;
        if (!horizonLogged && (ringsDone & far) == far) {
            horizonLogged = true;
            Awaken.LOG.info("[farsight] horizon (rings {}-{}, to {} km) ready {} ms after the first frame, {} ms after joining",
                    3, boundRings - 1, Rings.reach(boundRings - 1) / 1000, (now - bindNanos) / 1_000_000,
                    joinNanos == 0 ? -1 : (now - joinNanos) / 1_000_000);
        }
        if (ringsDone == all) Awaken.LOG.info("[farsight] all rings ready {} ms after the first frame; {}", (now - bindNanos) / 1_000_000, status());
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

    /** Development view: 0 normal, 1 generated-layer cells tinted magenta, 2 far terrain under the chunk mask red, 3 every far tree red and far terrain blue (autopilot {@code farsight debug <n>}). */
    static int debug;
    /**
     * Parts switched off for measuring (autopilot {@code farsight part <name> on|off}): 1 the per-pixel erosion detail,
     * 2 the trees, 4 the valley cloud, 8 the bake, 16 the workers' pacing.
     */
    static int partsOff;
    // seaunder: ring 0's sea drawn under the real chunks (4aa1b25f); seafloor: the far water's own floor (0cbdd9e8)
    // city: City Sight, the real chunks' blocks (Far Sight 3)
    private static final String[] PARTS = {"detail", "trees", "mist", "bake", "pace", "steady", "seaunder", "seafloor", "city"};

    /** Autopilot: {@code farsight on|off|status|debug <n>}. */
    public static String autopilot(String arg) {
        String a = arg.trim();
        if (a.startsWith("debug")) {
            debug = a.length() > 5 ? Integer.parseInt(a.substring(5).trim()) : 0;
            return "debug view " + debug;
        }
        if (a.startsWith("wateredge")) return "water edges " + dev.awaken.turbo.wateredge.WaterEdge.autopilot(a.substring(9));
        if (a.startsWith("time parts")) {
            FarRenderer.partsTiming = a.endsWith("on");
            return "parts timed " + FarRenderer.partsTiming;
        }
        if (a.startsWith("part ")) {
            String[] w = a.substring(5).trim().split("\\s+");
            for (int i = 0; i < PARTS.length; i++) {
                if (!PARTS[i].equals(w[0])) continue;
                if (w.length > 1 && w[1].equals("off")) partsOff |= 1 << i;
                else partsOff &= ~(1 << i);
                return "parts off " + partsOff;
            }
            return "unknown part " + w[0] + " (detail|trees|mist|bake|pace|steady|seaunder|seafloor|city)";
        }
        switch (a) {
            case "on" -> {
                forced = true;
                if (ModList.get().isLoaded("distanthorizons")) return "on, but Distant Horizons is installed: Far Sight stays off";
            }
            case "off" -> {
                forced = false;
                unbind();
            }
            case "setting" -> {
                forced = null;
            }
            case "status" -> {
            }
            default -> {
                return "unknown: " + arg.trim() + " (on|off|setting|status|debug <n>|part <name> on|off)";
            }
        }
        return status();
    }

    static String status() {
        if (field == null) return enabled() ? "on, not drawn yet" : "off";
        StringBuilder rings = new StringBuilder();
        for (int r = 0; r < Rings.RINGS; r++) rings.append(r == 0 ? "" : ",").append(field.missing[r]);
        long cols = workers.columnCount.get();
        double cpu = cpuFrames == 0 ? -1 : cpuNanos / 1e6 / cpuFrames;
        cpuNanos = 0;
        cpuFrames = 0;
        double[] gpu = renderer.gpuMillis();
        double[] parts = renderer.partMillis();
        StringBuilder split = new StringBuilder();
        if (parts != null) {
            split.append(" split_ms=[rings");
            for (int r = 0; r < Rings.RINGS; r++) split.append(String.format(Locale.ROOT, " %.3f", parts[r]));
            split.append(String.format(Locale.ROOT, " city %.3f trees %.3f]", parts[Rings.RINGS], parts[Rings.RINGS + 1]));
        }
        return String.format(Locale.ROOT,
                "%s gpu_ms=%.3f bake_ms=%.3f cpu_ms=%.3f rest_us=%d missing=[%s] queued=%d workers=%d pieces=%d cells=%d trees=%d (%d draws) column=%.1fus probes=%.1f/column (%d columns) generated=%d/%d city=%d/%d chunks (%d from disk, %d asked ahead, %d meshing, %.1f ms each, %d empty) drawn=%d chunks %d quads %d draws vram=%dMB",
                drawing() ? "drawing" : enabled() ? "on" : "off", gpu[0], gpu[1], cpu, workers.restMicros, rings, workers.queued(), workers.active, renderer.pieces, renderer.cells, renderer.treesDrawn, renderer.treeDraws,
                cols == 0 ? 0.0 : workers.columnNanos.get() / 1000.0 / cols, cols == 0 ? 0.0 : workers.probes.get() / (double) cols, cols,
                field.genUploaded, field.genAsked, voxels.resident(), VoxelCapture.captured(), VoxelCapture.fromDisk.get(), dev.awaken.farsight.voxel.VoxelAhead.asked(), voxels.inFlight.get(),
                voxels.meshed.get() == 0 ? 0.0 : voxels.meshNanos.get() / 1e6 / voxels.meshed.get(), voxels.empty.get(), voxels.chunksDrawn, voxels.quadsDrawn, voxels.commands(),
                (field.memoryBytes() + renderer.memoryBytes() + voxels.memoryBytes()) >> 20) + split;
    }
}
