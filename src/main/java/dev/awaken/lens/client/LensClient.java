package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.awaken.Awaken;
import dev.awaken.compat.Companions;
import dev.awaken.compat.client.FarTerrain;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.UiHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.List;

/**
 * Lens, AWAKEN's light layer (docs/plan-pack.html, SPEC §14): a short post chain run first in the frame's post effects,
 * before the gameplay ones (dreams, blight, the Veil), so those still read on top. It hazes the distance toward the
 * world's own fog colour, blooms what glows, grades each dimension its own way and, on CINEMATIC, draws light shafts
 * from the sun. Built on vanilla post chains, so it runs on both of 26.3's renderers; off costs nothing.
 */
public final class LensClient {
    private LensClient() {}

    static final Identifier SWIFT = Awaken.id("lens_swift");
    static final Identifier CINEMATIC = Awaken.id("lens_cinematic");
    static final Identifier ULTRA = Awaken.id("lens_ultra");
    /** Lens 2 (SPEC §22): the frame's matrices in floats ({@code awaken:lens_frame}) and the terrain's heights. */
    static final Identifier FRAME = Awaken.id("textures/effect/lens_frame.png");
    static final Identifier HEIGHT = Awaken.id("textures/effect/lens_height.png");
    /** The texture the chains read as {@code awaken:lens_params} (post chains look under textures/effect/). */
    static final Identifier PARAMS = Awaken.id("textures/effect/lens_params.png");
    /** Far terrain's depth ({@code awaken:lens_far_depth}): Distant Horizons draws without the game's depth. */
    static final Identifier FAR_DEPTH = Awaken.id("textures/effect/lens_far_depth.png");

    private static LensParams params;
    // Lens 2's light (Cinematic and Ultra): created the first time either tier is chosen, kept until the game closes
    private static LensFrame lensFrame;
    private static LensHeightfield heightfield;
    private static boolean lit, sunHere;
    private static float lightStrength, rain, warmth;
    private static boolean moonLight;
    // toward the light (the sun, or the moon by night) and the sunrise colour (0xRRGGBB), for the ambient particles
    private static final Vector3f lightDir = new Vector3f(0f, 1f, 0f);
    private static int warm = 0xFFFFFF;
    // Lens 2's clouds (L4): this frame's cloud layer height and colour (the game's own), whether Lens draws them here
    private static float cloudHeight = 192.33f;
    private static int cloudColour;
    private static boolean cloudsHere;
    /**
     * Development (autopilot lensdebug): 0 normal; 1 pass-through; 2 depth masks; 3 far-terrain depth raw; 4 haze amount;
     * Lens 2 (Cinematic, Ultra): 5 light buffer, 6 normals and direct sun, 7 the light alone, 8 the classic Cinematic.
     */
    private static int debug;
    private static boolean active, snap = true, zeroToOne;
    private static ResourceKey<Level> lookFor;
    // the camera's biome when it has a look of its own (null otherwise), sampled every 10 ticks
    private static String biomeFor;
    private static int biomeClock;
    private static final float[] current = Look.neutral(), target = Look.neutral();
    // the place's own look (the biome's, else the dimension's; or a story moment's), before any place look over it
    private static final float[] base = Look.neutral();
    // place looks over the biome's (SPEC §28.5: a plain cave's own, a seep's violet), each weighted 0..1 by its owner
    // every tick, blended in layer order; resolved again when the id or the looks change
    static final int PLACE_LAYERS = 3; // 0 a plain cave, 1 a seep (Delve), 2 a land far above you (Living Wilds' dread)
    private static final String[] placeId = new String[PLACE_LAYERS];
    private static final float[] placeWeight = new float[PLACE_LAYERS];
    private static final float[][] placeLook = {Look.neutral(), Look.neutral(), Look.neutral()};
    private static final boolean[] placeKnown = new boolean[PLACE_LAYERS], placeStale = new boolean[PLACE_LAYERS];
    // a story moment's look over the dimension's (the Ritual of the Bell: awaken:ritual, awaken:void), its easing rate
    private static @org.jspecify.annotations.Nullable String overrideLook;
    private static boolean overrideChanged;
    private static float ease = 0.06f;
    // a story scene keeps Lens on although the setting is off (a cutscene): after it, this many ticks to fade to neutral
    private static int linger;
    private static boolean wasOff;
    private static final float[] NEUTRAL = Look.neutral();
    // texel 13: the tear from black (progress per tick), crushed blacks and extra bloom, eased toward their targets
    private static float tear, tearStep, crush, crushTarget, bloomBoost, bloomTarget;
    // reused every frame: the sun's direction and where it lands on screen
    private static final Vector3f sunDir = new Vector3f();
    private static final Vector4f clip = new Vector4f();
    private static final Matrix4f viewProj = new Matrix4f();

    // Lens 2 spreads its clouds and light over frames (the lensTemporal setting, or the autopilot's override for A/Bs)
    private static boolean temporal = true;
    private static @org.jspecify.annotations.Nullable Boolean forcedTemporal;

    /** Autopilot {@code lens temporal on|off|setting}: the A/B switch for the interleaved clouds and light. */
    public static String temporal(String arg) {
        forcedTemporal = switch (arg.trim()) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
        return "temporal " + (forcedTemporal != null ? forcedTemporal : "from the setting");
    }

    // measurement only (autopilot `lens chain <id>`): a probe chain in place of Cinematic's or Ultra's (tools/lens/probes.py)
    private static @org.jspecify.annotations.Nullable Identifier forcedChain, lastForced;

    /** Autopilot {@code lens chain <name>|off}: runs awaken:lens_probe_<name> instead of the Look's chain (Cinematic or Ultra on). */
    public static String chain(String name) {
        String n = name.trim();
        forcedChain = n.isEmpty() || n.equals("off") ? null : Awaken.id("lens_probe_" + n);
        return "chain " + (forcedChain == null ? "from the Look" : forcedChain);
    }

    public static void debug(int mode) {
        debug = Math.clamp(mode, 0, 8);
        Awaken.LOG.info("[lens] debug view {}", debug);
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("lens_looks"),
                (ResourceManagerReloadListener) manager -> {
                    Looks.reload(manager);
                    lookFor = null; // re-resolve the current place's look
                    biomeFor = null;
                    java.util.Arrays.fill(placeStale, true);
                }));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        // after every other fog listener (Blight, the Veil): Lens hazes toward the fog the player actually sees
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ViewportEvent.RenderFog.class, LensClient::frame);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterLevel.class, LensClient::afterLevel);
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent.class, LensParticles::register);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent.class, LensClient::extract);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
            if (heightfield != null && e.getLevel().isClientSide()) heightfield.loaded(e.getChunk());
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lookFor = null;
            biomeFor = null;
            snap = true;
            active = false;
            lit = false;
            overrideLook = null;
            linger = 0;
            tear = tearStep = crush = crushTarget = bloomBoost = bloomTarget = 0f;
            java.util.Arrays.fill(placeWeight, 0f);
            return;
        }
        AwakenConfig.Client.LensLook setting = AwakenConfig.Client.LENS.get();
        temporal = forcedTemporal != null ? forcedTemporal : AwakenConfig.Client.LENS_TEMPORAL.get();
        boolean off = setting == AwakenConfig.Client.LensLook.OFF;
        // a story scene's look plays like a cutscene even with Lens off (unless the player said no), then fades out
        boolean scene = overrideLook != null || tearStep > 0 || linger > 0;
        if (linger > 0) linger--;
        boolean on = (!off || scene && scenesAllowed()) && !Companions.shaderPackActive();
        Identifier want = !on ? null : switch (setting) {
            case CINEMATIC -> CINEMATIC;
            case ULTRA -> ULTRA;
            default -> SWIFT;
        };
        if (want != null && want != SWIFT && forcedChain != null) want = forcedChain;
        // the server replaces this list whenever it syncs post effects, so Lens re-asserts itself every tick, first
        List<Identifier> effects = player.getActivePostEffects();
        if (lastForced != null && lastForced != want) effects.remove(lastForced);
        lastForced = want == forcedChain ? forcedChain : null;
        if (want != SWIFT) effects.remove(SWIFT);
        if (want != CINEMATIC) effects.remove(CINEMATIC);
        if (want != ULTRA) effects.remove(ULTRA);
        boolean wasLit = lit;
        lit = want == CINEMATIC || want == ULTRA || want != null && want == forcedChain;
        if (want != null) {
            ensureParams();
            if (lit) ensureLight();
            if (lit && !wasLit) lensFrame.reset();
            int at = effects.indexOf(want);
            if (at != 0) {
                if (at > 0) effects.remove(at);
                effects.addFirst(want);
            }
        }
        active = want != null;

        ResourceKey<Level> dim = mc.level.dimension();
        String biome = biomeFor;
        if (dim != lookFor || ++biomeClock >= 10) {
            biomeClock = 0;
            biome = mc.level.getBiome(mc.gameRenderer.mainCamera().blockPosition()).unwrapKey()
                    .map(k -> k.identifier().toString()).filter(Looks::hasBiome).orElse(null);
        }
        boolean placeChanged = dim != lookFor || !java.util.Objects.equals(biome, biomeFor);
        if (placeChanged || overrideChanged || off != wasOff) {
            // a new dimension eases in over about 1.5 s, one biome's look into another's over about 3 s; a story scene
            // keeps the pace override/clearOverride set
            if (placeChanged && !overrideChanged && overrideLook == null) ease = dim != lookFor ? 0.06f : 0.03f;
            if (dim != lookFor && lensFrame != null) lensFrame.reset();
            // with Lens off the look rests at neutral, so a scene eases in from (and back out to) the plain frame
            if (overrideLook != null) Looks.resolve(overrideLook, null, base);
            else if (off) System.arraycopy(NEUTRAL, 0, base, 0, Look.SIZE);
            else Looks.resolve(dim.identifier().toString(), biome, base);
            lookFor = dim;
            biomeFor = biome;
            overrideChanged = false;
            wasOff = off;
        }
        // the place looks over it: never while Lens is off, never over a story moment's look
        System.arraycopy(base, 0, target, 0, Look.SIZE);
        if (!off && overrideLook == null) {
            for (int l = 0; l < PLACE_LAYERS; l++) {
                if (placeStale[l]) {
                    placeKnown[l] = placeId[l] != null && Looks.has(placeId[l]);
                    if (placeKnown[l]) Looks.resolve(placeId[l], null, placeLook[l]);
                    placeStale[l] = false;
                }
                float w = placeWeight[l];
                if (!placeKnown[l] || w <= 0.001f) continue;
                float[] p = placeLook[l];
                for (int i = 0; i < Look.SIZE; i++) target[i] += (p[i] - target[i]) * w;
            }
        }
        // Lens 2's sun shadows need the terrain's heights, only where there is a sun
        sunHere = mc.level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
        if (lit && sunHere) {
            var at = mc.gameRenderer.mainCamera().blockPosition();
            heightfield.tick(mc.level, at.getX() >> 4, at.getZ() >> 4);
        }
        if (lit && !scene) LensParticles.tick(mc, want == ULTRA, biomeFor, lightStrength, lightDir, warm, warmth, sunHere ? heightfield : null);
        // a hard cut on joining a world
        float k = snap ? 1f : ease;
        for (int i = 0; i < Look.SIZE; i++) current[i] += (target[i] - current[i]) * k;
        snap = false;
        if (tearStep > 0) {
            tear = Math.min(1f, tear + tearStep);
            if (tear >= 1f) {
                tear = 0f; // fully open: the frame is the game's again
                tearStep = 0f;
            }
        }
        crush += (crushTarget - crush) * 0.08f;
        bloomBoost += (bloomTarget - bloomBoost) * (bloomTarget > bloomBoost ? 0.35f : 0.05f);
    }

    /**
     * A story moment's look in place of the dimension's (an id in looks.json, such as {@code awaken:ritual}), eased in
     * over about {@code fadeTicks}. It plays even while Lens is off, for the scene's length (client config
     * {@code lensInScenes}); never while a shaderpack is on, where callers carry on without it ({@link #active()}).
     */
    public static void override(String lookId, int fadeTicks) {
        if (lookId.equals(overrideLook)) return;
        overrideLook = lookId;
        overrideChanged = true;
        ease = rate(fadeTicks);
    }

    /**
     * A place look over the biome's: {@code lookId} (an exact key in lens/*.json, such as {@code awaken:seep}) blended over
     * the place's own look by {@code weight} (0..1), in layer order (0 first, then 1). The owner sets it every tick it
     * holds (its weight already eased); Lens eases toward the blend as it does between biomes. It never plays while Lens
     * is off (unlike a story moment) and a story moment's look ({@link #override}) wins over it. Delve's senses use layer 0
     * for a plain cave's own look and layer 1 for a seep (SPEC §28.5).
     */
    public static void placeLook(int layer, @org.jspecify.annotations.Nullable String lookId, float weight) {
        if (layer < 0 || layer >= PLACE_LAYERS) return;
        if (!java.util.Objects.equals(lookId, placeId[layer])) {
            placeId[layer] = lookId;
            placeStale[layer] = true;
        }
        placeWeight[layer] = lookId == null ? 0f : Math.clamp(weight, 0f, 1f);
    }

    /** The moment's look playing now ({@link #override}), or null for the place's own. */
    public static @org.jspecify.annotations.Nullable String overriding() {
        return overrideLook;
    }

    /** Back to the dimension's own look over about {@code fadeTicks} (with Lens off: to the plain frame, then off). */
    public static void clearOverride(int fadeTicks) {
        if (overrideLook == null) return;
        overrideLook = null;
        overrideChanged = true;
        ease = rate(fadeTicks);
        linger = Math.max(linger, fadeTicks + 10);
    }

    /** Whether story scenes may turn Lens on while it's off (client config {@code lensInScenes}). */
    private static boolean scenesAllowed() {
        try {
            return AwakenConfig.Client.LENS_IN_SCENES.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** The black opens from the centre over {@code ticks} (the awakening); the frame stays black until it starts. */
    public static void tear(int ticks) {
        tear = 0.0001f;
        tearStep = 1f / Math.max(1, ticks);
    }

    /** Crushed blacks (0..1) and extra bloom (0..1, eased up fast and down slowly) for the ritual's moments. */
    public static void ritual(float crushTo, float bloomTo) {
        crushTarget = Math.clamp(crushTo, 0f, 1f);
        bloomTarget = Math.clamp(bloomTo, 0f, 1f);
    }

    /**
     * A bloom pulse (0..1): the glow lifts at once and eases away over about a second (a power's release, SPEC §32). It rides the same
     * boost the ritual's bloom does and never lowers it; with Lens off it draws nothing.
     */
    public static void pulse(float strength) {
        bloomBoost = Math.max(bloomBoost, Math.clamp(strength, 0f, 1f));
    }

    // moving fast through the air: the target the Feel Director sets, and what's drawn (eased per frame)
    private static float motionTarget, motionTx = 0.5f, motionTy = 0.5f, motion, motionX = 0.5f, motionY = 0.5f;
    private static long motionAt;

    /**
     * Speed through the air (SPEC §14, §17): a blur toward where you're heading that smears the edges of the frame, with a
     * touch of chroma. {@code strength} 0..1, ({@code dirX}, {@code dirY}) the heading on screen (0..1 from the bottom
     * left). Eased over a few frames here; scaled by the FOV-effects slider; 0 while a story scene or the tear runs.
     * Callers keep it 0 with reduced motion. With Lens off it draws nothing (FxScreen's speed lines are the fallback).
     */
    public static void motion(float strength, float dirX, float dirY) {
        motionTarget = Math.clamp(strength, 0f, 1f);
        motionTx = Math.clamp(dirX, 0f, 1f);
        motionTy = Math.clamp(dirY, 0f, 1f);
    }

    /** Whether Lens is drawing this frame (off, or stepped aside for a shaderpack, means callers use their fallbacks). */
    public static boolean active() {
        return active;
    }

    /**
     * Whether Lens 2 marches the Living Wilds' storm cells in its cloud volume this frame (Cinematic or Ultra, under an
     * overworld sky); otherwise their own fallback draws them (SPEC §22 "Storm cells").
     */
    public static boolean drawsStormCells() {
        return active && lit && sunHere && lensFrame != null;
    }

    private static float rate(int fadeTicks) {
        // about 95% of the way after fadeTicks
        return fadeTicks <= 1 ? 1f : (float) (1.0 - Math.pow(0.05, 1.0 / fadeTicks));
    }

    private static void ensureLight() {
        if (lensFrame != null) return;
        // registered before the chain that reads them first loads: a post pass keeps the texture it found then
        lensFrame = new LensFrame();
        heightfield = new LensHeightfield();
        Minecraft.getInstance().getTextureManager().register(FRAME, lensFrame.texture);
        Minecraft.getInstance().getTextureManager().register(HEIGHT, heightfield.texture);
        Awaken.LOG.info("[lens] light ready (heightfield {}x{}, {} mips)", LensHeightfield.SIZE, LensHeightfield.SIZE, LensHeightfield.LEVELS);
    }

    /**
     * Lens 2's clouds (L4): where the game would draw its clouds this frame (its cloud colour has alpha; the player's
     * Clouds setting isn't Off) and the place's look wants Lens's, Lens takes their height and colour and hides vanilla's
     * for this frame only (the render state's colour loses its alpha; the setting itself is never touched).
     */
    private static void extract(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent e) {
        var state = e.getRenderState();
        cloudsHere = active && lit && current[Look.CLOUDS] > 0f && net.minecraft.util.ARGB.alpha(state.cloudColor) > 0
                && e.getOptionsRenderState().cloudStatus != net.minecraft.client.CloudStatus.OFF;
        if (!cloudsHere) return;
        cloudColour = state.cloudColor;
        cloudHeight = state.cloudHeight;
        state.cloudColor &= 0x00FFFFFF;
    }

    /** Just before the post effects: Lens 2's frame values (the exact matrices this frame drew with, the light). */
    private static void afterLevel(RenderLevelStageEvent.AfterLevel e) {
        if (!active) return;
        if (lit && lensFrame != null) writeFrame(e);
        // both textures are written for this frame: the same values as one uniform block (Tiles binds it on Apple's path)
        if (params != null) {
            if (block == null) block = new LensBlock();
            block.write(lit ? lensFrame : null, params);
            dev.awaken.turbo.tiles.Tiles.lensBlock(block.buffer);
        }
    }

    private static @org.jspecify.annotations.Nullable LensBlock block;

    private static void writeFrame(RenderLevelStageEvent.AfterLevel e) {
        var state = e.getLevelRenderState();
        float lx = moonLight ? -sunDir.x : sunDir.x, ly = moonLight ? -sunDir.y : sunDir.y, lz = moonLight ? -sunDir.z : sunDir.z;
        // the clouds' cover: the look's, thickening toward overcast in rain and storms (not for the rain a Rolling Storm
        // brings: its own cell is the cloud over you)
        float weather = Math.max(0f, rain - dev.awaken.living.client.StormSky.localRain());
        float cover = cloudsHere ? Math.min(0.95f, current[Look.CLOUDS] + weather * 0.4f) : 0f;
        long ticks = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        lensFrame.clouds(cloudsHere ? cloudHeight : 0f, cover, (ticks % 768_000L) * 0.04, cloudColour);
        lensFrame.write(state.cameraRenderState, state.playerRenderState, zeroToOne, lx, ly, lz, lightStrength, moonLight, rain,
                sunHere ? heightfield : null, temporal);
    }

    private static void ensureParams() {
        if (params != null) return;
        params = new LensParams();
        Minecraft.getInstance().getTextureManager().register(PARAMS, params.texture);
        Minecraft.getInstance().getTextureManager().register(FAR_DEPTH, dev.awaken.compat.client.FarTerrain.depthTexture());
        zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Awaken.LOG.info("[lens] ready ({} depth)", zeroToOne ? "zero-to-one" : "minus-one-to-one");
    }

    /** Once a frame, after the fog is set up and before the world and its post effects are drawn. */
    private static void frame(ViewportEvent.RenderFog e) {
        if (!active || params == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = (float) e.getPartialTick();
        // the camera's far plane, as Camera.update sets it
        float far = Math.max(mc.options.getEffectiveRenderDistance() * 16 * 4f, mc.options.cloudRange().get() * 16f);
        float u = 0.5f, v = 0.5f, visible = 0f, sunset = 0f, sr = 1f, sg = 1f, sb = 1f;
        lightStrength = 0f;
        Camera camera = mc.gameRenderer.mainCamera();
        if (mc.level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD) {
            // where SkyRenderer puts the sun: up, turned about X by the sun angle, then about Y by -90 degrees
            float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial) * (float) (Math.PI / 180.0);
            sunDir.set(0f, 1f, 0f).rotateX(angle).rotateY((float) (-Math.PI / 2));
            camera.getViewRotationProjectionMatrix(viewProj).transform(clip.set(sunDir.x, sunDir.y, sunDir.z, 0f));
            if (clip.w > 1.0e-4f) {
                u = clip.x / clip.w * 0.5f + 0.5f;
                v = clip.y / clip.w * 0.5f + 0.5f;
                float outside = Math.max(Math.max(-u, u - 1f), Math.max(-v, v - 1f));
                float onScreen = 1f - smooth(Math.max(outside, 0f) / 0.6f);
                visible = Math.clamp(sunDir.y * 5f + 0.25f, 0f, 1f) * (1f - mc.level.getRainLevel(partial)) * onScreen;
            }
            // Lens 2's light: the sun by day, a faint moon by night, both dimmed by rain; nothing at the horizon crossing
            rain = mc.level.getRainLevel(partial);
            float day = smooth((sunDir.y + 0.02f) / 0.14f), night = smooth((-sunDir.y + 0.02f) / 0.14f) * 0.4f;
            moonLight = night > day;
            // dimmed by the world's weather, not by a Rolling Storm's local rain: the sun still lights its towers and the
            // land beside it, and its own shadow darkens the land under it (SPEC §22 "Storm cells")
            float skyRain = dev.awaken.living.client.StormSky.serverRain(rain), skyThunder = dev.awaken.living.client.StormSky.serverThunder(
                    mc.level.getThunderLevel(partial));
            lightStrength = Math.max(day, night) * (1f - 0.85f * skyRain) * (1f - 0.5f * skyThunder);
            Vector4fc glow = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, partial);
            sr = glow.x();
            sg = glow.y();
            sb = glow.z();
            sunset = glow.w();
            lightDir.set(sunDir).mul(moonLight ? -1f : 1f);
            warm = Math.clamp(Math.round(sr * 255f), 0, 255) << 16 | Math.clamp(Math.round(sg * 255f), 0, 255) << 8 | Math.clamp(Math.round(sb * 255f), 0, 255);
            warmth = sunset;
        }
        var fog = e.getFogData();
        // motion eases toward its target by the frame's real time (the same feel at 60 and 240 fps)
        long nowNs = System.nanoTime();
        float dt = motionAt == 0 ? 0f : Math.min(0.1f, (nowNs - motionAt) / 1.0e9f);
        motionAt = nowNs;
        boolean scene = overrideLook != null || tear > 0 || tearStep > 0;
        float want = scene ? 0f : motionTarget * mc.options.fovEffectScale().get().floatValue();
        float k = 1f - (float) Math.exp(-dt * 9.0);
        motion += (want - motion) * k;
        motionX += (motionTx - motionX) * k;
        motionY += (motionTy - motionY) * k;
        if (motion < 0.003f) motion = 0f;
        params.write(debug, current, far, zeroToOne, u, v, visible, sunset, sr, sg, sb, fog.color.x(), fog.color.y(), fog.color.z(),
                // a far renderer moves vanilla's chunk fog out; Lens's haze keeps vanilla's render distance
                FarTerrain.renderDistanceFogEnd(fog.renderDistanceEnd), fog.environmentalEnd, tear > 0 ? Math.min(1f, tear + tearStep * partial) : 0f, crush, bloomBoost,
                UiHooks.focus(), motion, motionX, motionY,
                FarTerrain.active(), FarTerrain.readable(), FarTerrain.reverseZ(), FarTerrain.zeroToOne(), FarTerrain.near(), FarTerrain.far());
        params.uploadIfChanged();
    }

    private static float smooth(float x) {
        float t = Math.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
