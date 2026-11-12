package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.awaken.Awaken;
import dev.awaken.compat.Companions;
import dev.awaken.compat.client.FarTerrain;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.UiHooks;
import dev.awaken.lens.LensHeights;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
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
    // Lite (SPEC §22 "Lite"): the Air without Lens 2's light. It reads the frame's values (the view, the sun, the clouds,
    // storm cells) and none of the light's textures (no heightfield, block map, sun map or lit air, and none of their
    // tick work)
    static final Identifier LITE = Awaken.id("lens_lite");
    // the hand's own depth (LensHand): which pixels are the hand and what it holds
    // (a chain's "awaken:lens_hand_depth" is this path: the game reads a chain's textures from textures/effect/. The
    // first build registered the bare name, the chain found nothing there and read the game's missing-texture
    // checkerboard as the hand: two quarters of the screen unlit, run 42985247494)
    static final Identifier HAND = Awaken.id("textures/effect/lens_hand_depth.png");
    private static LensHand hand;
    // light other parts of the mod draw for Lens to lay over its picture (LensGlow, SPEC §22 "Light over the Air")
    static final Identifier GLOW = Awaken.id("textures/effect/lens_glow.png");
    private static LensGlow glow;
    static final Identifier CINEMATIC = Awaken.id("lens_cinematic");
    static final Identifier ULTRA = Awaken.id("lens_ultra");
    // their Tiles twins (SPEC §22 "Steady 60"; tools/lens/block.py): the same chains with the composite that reads Lens's
    // values from the uniform block alone, run while Tiles hands that block over
    static final Identifier CINEMATIC_TILES = Awaken.id("lens_cinematic_tiles");
    static final Identifier ULTRA_TILES = Awaken.id("lens_ultra_tiles");
    // the twins' two composites, by their place in the chain (tools/lens/block.py checks them): the calm one, then the
    // twin's own; Tiles leaves one out every frame (leftOut)
    private static final int CALM_AT = 12, FULL_AT = 13;
    private static final String CINEMATIC_CALM = CINEMATIC_TILES + "/" + CALM_AT, CINEMATIC_FULL = CINEMATIC_TILES + "/" + FULL_AT,
            ULTRA_CALM = ULTRA_TILES + "/" + CALM_AT, ULTRA_FULL = ULTRA_TILES + "/" + FULL_AT;
    // this frame's choice (afterLevel): the calm composite runs only once LensBlock has found the frame calm
    private static String leaveCinematic = CINEMATIC_CALM, leaveUltra = ULTRA_CALM;
    private static boolean calmLast;
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
    // the Sun Pass's block map (SPEC §22 "Steady Light"): which blocks near the camera stop the sun
    private static LensBlocks blocks;
    static final Identifier BLOCKS = Awaken.id("textures/effect/lens_blocks.png");
    // the lit air (SPEC §22 "Steady Light"): which air near the camera the sun reaches, for its rays
    private static LensLit litAir;
    // the sun map (SPEC §22): the height the sun reaches in each column, swept on a worker in place of a pass
    private static LensSunMap sunMap;
    static final Identifier SUNMAP = Awaken.id("textures/effect/lens_sunmap.png");
    static final Identifier LIT = Awaken.id("textures/effect/lens_lit.png");
    private static boolean sunPass = true;
    private static @org.jspecify.annotations.Nullable Boolean forcedSunPass;

    /** Autopilot {@code lens sunpass on|off|setting}: the A/B switch for the block map's shadows. */
    public static String sunPass(String arg) {
        forcedSunPass = switch (arg.trim()) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
        return "sun pass " + (forcedSunPass != null ? forcedSunPass : "from the setting");
    }
    private static boolean rays;
    private static @org.jspecify.annotations.Nullable Boolean forcedRays;

    /** Autopilot {@code lens rays on|off|setting}: the A/B switch for the lit air (the sun's rays). */
    public static String rays(String arg) {
        forcedRays = switch (arg.trim()) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
        return "rays " + (forcedRays != null ? forcedRays : "on with the Sun Pass") + (litAir != null ? ", " + litAir.report() : "");
    }

    // what Lens's own tick work costs the render thread (the heightfield, the block map, the lit air's hand-over): the
    // sum, the worst and the count since the last report
    private static final long[] costSum = new long[3], costMax = new long[3];
    private static int costTicks;

    private static void cost(int what, long nanos) {
        costSum[what] += nanos;
        costMax[what] = Math.max(costMax[what], nanos);
        if (what == 0) costTicks++;
    }

    /** Autopilot {@code lens cost}: the tick work's mean and worst time since the last call, and starts over. */
    public static String cost() {
        int n = Math.max(1, costTicks);
        String out = String.format(java.util.Locale.ROOT,
                "tick cost over %d ticks, mean/worst ms: heightfield %.3f/%.2f, block map %.3f/%.2f, lit air %.3f/%.2f%s", costTicks,
                costSum[0] / 1.0e6 / n, costMax[0] / 1.0e6, costSum[1] / 1.0e6 / n, costMax[1] / 1.0e6, costSum[2] / 1.0e6 / n, costMax[2] / 1.0e6,
                (litAir != null ? "; " + litAir.report() : "") + (sunMap != null ? "; " + sunMap.report() : ""));
        java.util.Arrays.fill(costSum, 0L);
        java.util.Arrays.fill(costMax, 0L);
        costTicks = 0;
        return out;
    }

    /** How much of the sun reaches a point by the lit air, 0..1; -1 where it has no answer (LensParticles' motes). */
    static float litAir(double x, double y, double z) {
        return rays && litAir != null ? litAir.at(x, y, z) : -1f;
    }
    private static boolean lit, sunHere;
    // the chain reads the frame's values: Cinematic, Ultra and Lite
    private static boolean framed;
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
    // the look as drawn: `current` with the player's grade over it (LensGrades)
    private static final float[] graded = Look.neutral();
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

    // the Air (SPEC §22 "Steady Light"): the lensAir setting, or the autopilot's override for A/Bs
    private static boolean air = true;
    private static @org.jspecify.annotations.Nullable Boolean forcedAir;
    // the game's sky colour and star brightness this frame (what it drew behind the sun, moon and stars)
    private static float skyR, skyG, skyB, starBrightness;

    /** Autopilot {@code lens air on|off|setting}: the A/B switch for the Air. */
    public static String air(String arg) {
        forcedAir = switch (arg.trim()) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
        return "air " + (forcedAir != null ? forcedAir : "from the setting");
    }

    /** Autopilot {@code lens grade <id>|setting}: a grade for the following scenes (SPEC §22 "Grades"). */
    public static String grade(String arg) {
        return LensGrades.force(arg);
    }

    /** The grade in use, by the name the Look page shows. */
    public static String gradeName() {
        return LensGrades.current().name();
    }

    /** The Look page's click: the next grade ({@code step} 1) or the one before (-1), set now and saved. Returns its name. */
    public static String stepGrade(int step) {
        var g = LensGrades.next(step);
        LensGrades.choose(g);
        return g.name();
    }

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

    // measurement only (autopilot `lens twin off|setting`): Cinematic and Ultra as written even while the block is live
    private static boolean noTwin;

    /** Autopilot {@code lens twin off|setting}: the A/B switch for the Tiles twins (SPEC §22 "Steady 60"). */
    public static String twin(String arg) {
        noTwin = arg.trim().equals("off");
        return "twin " + (noTwin ? "off" : "with Tiles' block") + (dev.awaken.turbo.tiles.Tiles.blockLive() ? " (block live)" : " (block not live)");
    }

    // measurement only (autopilot `lens calm off|setting`): the twin's own composite every frame, calm or not
    private static boolean noCalm;

    /** Autopilot {@code lens calm off|setting}: the A/B switch for the twins' calm composite (SPEC §22 "Steady 60"). */
    public static String calm(String arg) {
        noCalm = arg.trim().equals("off");
        return "calm composite " + (noCalm ? "off" : "on calm frames") + (calmLast ? " (last frame calm)" : " (last frame not calm)");
    }

    /**
     * Tiles, for each post pass: whether it is left out this frame. Each Tiles twin has two composites, both drawing the
     * whole of swap from the same inputs, and one is left out: the calm one, unless this frame's values (LensBlock.calm,
     * read from the very block the composites read) have every path it lacks switched off; then the twin's own.
     */
    public static boolean leftOut(String pass) {
        return pass.equals(leaveCinematic) || pass.equals(leaveUltra);
    }

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
                    LensGrades.reload(manager);
                    lookFor = null; // re-resolve the current place's look
                    biomeFor = null;
                    java.util.Arrays.fill(placeStale, true);
                    WorldProjection.recheck();
                }));
        // the exact matrices, from the projection the world was really drawn with (Lens, Pixels and Holo ask for them)
        WorldProjection.init(modBus);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        // after every other fog listener (Blight, the Veil): Lens hazes toward the fog the player actually sees
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ViewportEvent.RenderFog.class, LensClient::frame);
        // (before the others that shape the fog's colour: the edge hold pales it, Far Sight reads it last)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ViewportEvent.ComputeFogColor.class, LensClient::fogColour);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterLevel.class, LensClient::afterLevel);
        // last of all after the level: whatever drew light for Lens to lay has drawn it by now
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderLevelStageEvent.AfterLevel.class, e -> LensGlow.settle());
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent.class, LensParticles::register);
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent.class, LensParticles::pipelines);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent.class, LensClient::extract);
        // development: /awaken lens projection off|on|check (A/B shots of the CPU's estimate against the world's own);
        // /awaken lens sunpass|rays off|on|setting and /awaken lens paths on|off (the Sun Pass's parts one at a time, in
        // play: which of them a shadow that changes with where you stand comes from)
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                net.minecraft.commands.Commands.literal(Awaken.MODID).then(net.minecraft.commands.Commands.literal("lens")
                        .then(lensSwitch("sunpass", LensClient::sunPass))
                        .then(lensSwitch("rays", LensClient::rays))
                        .then(lensSwitch("paths", LensClient::paths))
                        .then(net.minecraft.commands.Commands.literal("small")
                                .then(net.minecraft.commands.Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(c -> {
                                            String line = small(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "what"));
                                            Awaken.LOG.info("[lens] {}", line);
                                            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(line));
                                            return 1;
                                        })))
                        .then(net.minecraft.commands.Commands.literal("projection")
                                .then(net.minecraft.commands.Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(c -> {
                                            String line = WorldProjection.command(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "what"));
                                            Awaken.LOG.info("[lens] {}", line);
                                            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(line));
                                            return 1;
                                        }))))));
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
            if (heightfield != null && e.getLevel().isClientSide()) heightfield.loaded(e.getChunk());
        });
    }

    /** A client command {@code /awaken lens <name> <what>} that sets one development switch and says what it is now. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> lensSwitch(
            String name, java.util.function.Function<String, String> set) {
        return net.minecraft.commands.Commands.literal(name)
                .then(net.minecraft.commands.Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> {
                            String line = set.apply(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "what"));
                            Awaken.LOG.info("[lens] {}", line);
                            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(line));
                            return 1;
                        }));
    }

    // The small shade's reach (SPEC §22 "The small shade's reach"): how wide a texel of the light buffer may be on a
    // surface, in the art's pixels (sixteenths of a block), where the contact shadows fade from and to, then the
    // ambient shade; an end not past its start leaves that shade whole at every distance.
    private static final float[] SMALL = {1f, 3f, 0f, 0f};
    private static float @org.jspecify.annotations.Nullable [] forcedSmall;
    /** The light buffer's height in texels (the chains' {@code awaken:lens_light} target). */
    private static final float LIGHT_ROWS = 540f;

    /**
     * {@code /awaken lens small <contact from> <contact to> <ambient from> <ambient to>} (autopilot {@code lens small}):
     * where the depth-made contact shadows and the ambient shade fade out, as the width of a light texel in the art's
     * pixels, for A/B shots and for tuning in play; {@code setting} puts the built-in reach back. An end not past its
     * start: no fade. The answer says what the numbers come to in blocks at the view in use.
     */
    public static String small(String arg) {
        String[] w = arg.trim().split("\\s+");
        if (w.length == 4) {
            try {
                forcedSmall = new float[] {Float.parseFloat(w[0]), Float.parseFloat(w[1]), Float.parseFloat(w[2]), Float.parseFloat(w[3])};
            } catch (NumberFormatException e) {
                return "lens small: four numbers (contact from, to; ambient from, to) or 'setting'";
            }
        } else {
            forcedSmall = null;
        }
        float[] s = forcedSmall != null ? forcedSmall : SMALL;
        // a texel is one art pixel wide this many blocks off, at the view in use
        double fov = Minecraft.getInstance().options.fov().get();
        double one = LIGHT_ROWS / (2.0 * Math.tan(Math.toRadians(fov / 2.0))) / 16.0;
        return String.format(java.util.Locale.ROOT, "small shade%s: contact shadows fade from %s to %s, ambient shade from %s to %s",
                forcedSmall != null ? "" : " (built in)", reach(s[0], s[1], one, true), reach(s[0], s[1], one, false),
                reach(s[2], s[3], one, true), reach(s[2], s[3], one, false));
    }

    /** One end of a reach in words: the texel width in art pixels and the blocks that is at this view, or "never". */
    private static String reach(float from, float to, double blocksAtOne, boolean start) {
        if (to <= from) return start ? "nowhere" : "nowhere (whole at every distance)";
        float v = start ? from : to;
        return String.format(java.util.Locale.ROOT, "%.1f px (%.0f blocks)", v, v * blocksAtOne);
    }

    private static boolean showPaths;

    /**
     * {@code /awaken lens paths on|off}: marks on every lit surface which part of the Sun Pass answered it, over the
     * answer itself (lens_light.fsh, pathMark): plain where the heightfield found nothing over the ray or a block
     * stopped the walk, lines across the sun's way where the lit air was sure, lines along it where the walk left the
     * map in the open, a checker where the walk met a section not read yet. For a shadow that changes with where you
     * stand: the mark under it says which part changed its mind.
     */
    public static String paths(String arg) {
        showPaths = arg.trim().equals("on");
        return "sun pass paths " + (showPaths ? "on: lines across the sun's way = lit air, lines along it = walk left the map, checker = not read yet" : "off");
    }

    private static void tick() {
        // measurement only (the GPU timing's trace): a tick ran in this frame, and whether the governor stood Lens back
        dev.awaken.turbo.pixels.Pixels.mark(tickBudget() < 500_000L ? 128 | 64 : 128);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lookFor = null;
            biomeFor = null;
            snap = true;
            active = false;
            lit = false;
            framed = false;
            overrideLook = null;
            linger = 0;
            tear = tearStep = crush = crushTarget = bloomBoost = bloomTarget = 0f;
            java.util.Arrays.fill(placeWeight, 0f);
            return;
        }
        AwakenConfig.Client.LensLook setting = AwakenConfig.Client.LENS.get();
        air = forcedAir != null ? forcedAir : AwakenConfig.Client.LENS_AIR.get();
        // (with the Air the clouds and light are always spread over frames: its clouds are walked exactly, so a carried
        // pixel is as true as a fresh one, and its larger light buffer is sized for it. Measured on the Dell at Euan's
        // settings: 13.0 ms of GPU time without, 11.0 with)
        temporal = forcedTemporal != null ? forcedTemporal : (AwakenConfig.Client.LENS_TEMPORAL.get() || air);
        sunPass = forcedSunPass != null ? forcedSunPass : AwakenConfig.Client.LENS_SUN_PASS.get();
        // the VFX tier is ImmersiveVFX alone: no Lens at all, story scenes included (without that mod it reads as Swift)
        boolean vfx = setting == AwakenConfig.Client.LensLook.VFX && Companions.immersiveVfxLoaded();
        boolean off = setting == AwakenConfig.Client.LensLook.OFF || vfx;
        // a story scene's look plays like a cutscene even with Lens off (unless the player said no), then fades out
        boolean scene = overrideLook != null || tearStep > 0 || linger > 0;
        if (linger > 0) linger--;
        boolean on = (!off || scene && !vfx && scenesAllowed()) && !Companions.shaderPackActive();
        Identifier want = !on ? null : switch (setting) {
            case LITE -> LITE;
            case CINEMATIC -> CINEMATIC;
            case ULTRA -> ULTRA;
            default -> SWIFT;
        };
        if (want != null && want != SWIFT && want != LITE && forcedChain != null) want = forcedChain;
        // Steady 60 (SPEC §22): while Tiles hands Lens's passes the live uniform block, Cinematic and Ultra run their twins,
        // whose composite reads its values from the block alone: the very values it reads now, without a texture read
        // beside each that made it a per-pixel register (some 4 ms of a 4K frame). A development view keeps the chain as
        // written (the twin has none)
        if (debug == 0 && !noTwin && dev.awaken.turbo.tiles.Tiles.blockLive()) {
            if (want == CINEMATIC) want = CINEMATIC_TILES;
            else if (want == ULTRA) want = ULTRA_TILES;
        }
        // Lite is the Air and nothing else: its chain has no other sky to fall back to
        if (want == LITE && forcedAir == null) air = true;
        // the server replaces this list whenever it syncs post effects, so Lens re-asserts itself every tick, first
        List<Identifier> effects = player.getActivePostEffects();
        if (lastForced != null && lastForced != want) effects.remove(lastForced);
        lastForced = want == forcedChain ? forcedChain : null;
        if (want != SWIFT) effects.remove(SWIFT);
        if (want != LITE) effects.remove(LITE);
        if (want != CINEMATIC) effects.remove(CINEMATIC);
        if (want != ULTRA) effects.remove(ULTRA);
        if (want != CINEMATIC_TILES) effects.remove(CINEMATIC_TILES);
        if (want != ULTRA_TILES) effects.remove(ULTRA_TILES);
        boolean wasFramed = framed;
        lit = want == CINEMATIC || want == ULTRA || want == CINEMATIC_TILES || want == ULTRA_TILES || want != null && want == forcedChain;
        framed = lit || want == LITE;
        if (want != null) {
            ensureParams();
            if (framed) ensureFrame();
            if (lit) ensureLight();
            if (framed && !wasFramed) lensFrame.reset();
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
            long t0 = System.nanoTime();
            heightfield.tick(mc.level, at.getX() >> 4, at.getZ() >> 4);
            long t1 = System.nanoTime();
            if (sunPass && blocks != null) blocks.tick(mc.level, at.getX(), at.getY(), at.getZ());
            if (sunMap != null) sunMap.tick(heightfield, moonLight ? -sunDir.x : sunDir.x, moonLight ? -sunDir.y : sunDir.y,
                    moonLight ? -sunDir.z : sunDir.z, at.getX(), at.getZ());
            cost(0, t1 - t0);
            cost(1, System.nanoTime() - t1);
        }
        rays = lit && sunHere && sunPass && air && (forcedRays != null ? forcedRays : true) && blocks != null && litAir != null;
        if (rays) {
            var at = mc.gameRenderer.mainCamera().blockPosition();
            long t0 = System.nanoTime();
            litAir.tick(blocks, moonLight ? -sunDir.x : sunDir.x, moonLight ? -sunDir.y : sunDir.y, moonLight ? -sunDir.z : sunDir.z,
                    at.getX(), at.getZ());
            cost(2, System.nanoTime() - t0);
        } else if (litAir != null) {
            litAir.forget();
        }
        if (lit && !scene) LensParticles.tick(mc, want == ULTRA || want == ULTRA_TILES, biomeFor, lightStrength, lightDir, warm, warmth, sunHere ? heightfield : null,
                air && sunHere, sunDir.y, rain);
        // a hard cut on joining a world
        float k = snap ? 1f : ease;
        for (int i = 0; i < Look.SIZE; i++) current[i] += (target[i] - current[i]) * k;
        // the player's grade over it; a story moment's look plays as it was authored, and with Lens off there is no
        // grade over the plain frame a scene eases back to
        LensGrades.tick(overrideLook != null || off);
        LensGrades.apply(current, graded);
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
    // texel 21: the camera under solid cover, eased (Swift's dark floor; SPEC §22)
    private static float under;
    // the camera's column for Swift's cover: its chunk's sections that may hold a block the heightmaps skip, its tops
    private static boolean[] camHas = new boolean[0];
    private static final int[] camCol = new int[2];
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
        return active && framed && sunHere && lensFrame != null;
    }

    /**
     * Whether this frame's chain lays LensGlow's layer over its picture: the chains that read the frame (Lite, Cinematic,
     * Ultra, whose last pass is lens_lay), never during a story's tear from black, which nothing may show through.
     */
    static boolean laysLight() {
        return active && framed && glow != null && tear <= 0f && tearStep <= 0f;
    }

    private static float rate(int fadeTicks) {
        // about 95% of the way after fadeTicks
        return fadeTicks <= 1 ? 1f : (float) (1.0 - Math.pow(0.05, 1.0 / fadeTicks));
    }

    /** The frame's values, for every chain that reads them (Lite too). */
    private static void ensureFrame() {
        if (lensFrame != null) return;
        // registered before the chain that reads it first loads: a post pass keeps the texture it found then
        lensFrame = new LensFrame();
        Minecraft.getInstance().getTextureManager().register(FRAME, lensFrame.texture);
    }

    private static void ensureLight() {
        if (heightfield != null) return;
        // registered before the chain that reads them first loads: a post pass keeps the texture it found then
        heightfield = new LensHeightfield();
        blocks = new LensBlocks();
        Minecraft.getInstance().getTextureManager().register(HEIGHT, heightfield.texture);
        litAir = new LensLit();
        sunMap = new LensSunMap();
        Minecraft.getInstance().getTextureManager().register(SUNMAP, sunMap.texture);
        Minecraft.getInstance().getTextureManager().register(BLOCKS, blocks.texture);
        Minecraft.getInstance().getTextureManager().register(LIT, litAir.texture);
        Awaken.LOG.info("[lens] light ready (heightfield {}x{}, {} mips)", LensHeightfield.SIZE, LensHeightfield.SIZE, LensHeightfield.LEVELS);
    }

    /**
     * Lens 2's clouds (L4): where the game would draw its clouds this frame (its cloud colour has alpha; the player's
     * Clouds setting isn't Off) and the place's look wants Lens's, Lens takes their height and colour and hides vanilla's
     * for this frame only (the render state's colour loses its alpha; the setting itself is never touched).
     */
    private static void extract(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent e) {
        var state = e.getRenderState();
        var sky = state.skyRenderState;
        if (sky.skyColor != null) {
            skyR = sky.skyColor.x();
            skyG = sky.skyColor.y();
            skyB = sky.skyColor.z();
        }
        starBrightness = sky.starBrightness;
        // (with the Air on, Lens's clouds are part of its sky: drawn whatever the Clouds video setting says)
        cloudsHere = active && framed && current[Look.CLOUDS] > 0f && net.minecraft.util.ARGB.alpha(state.cloudColor) > 0
                && (air || e.getOptionsRenderState().cloudStatus != net.minecraft.client.CloudStatus.OFF);
        if (!cloudsHere) return;
        cloudColour = state.cloudColor;
        cloudHeight = state.cloudHeight;
        state.cloudColor &= 0x00FFFFFF;
    }

    /** Just before the post effects: Lens 2's frame values (the exact matrices this frame drew with, the light). */
    private static void afterLevel(RenderLevelStageEvent.AfterLevel e) {
        if (!active) return;
        if (framed && lensFrame != null) writeFrame(e);
        // the hand is drawn next, into a depth of its own: Lens's light is for the world and stops at the hand, which
        // it can only tell from a wall at arm's length by that depth (SPEC §22 "Reach and cover")
        if (params != null && hand != null) {
            var handTarget = dev.awaken.turbo.pixels.PixelsHooks.handTarget();
            params.hand(hand.point(handTarget != null ? handTarget.getDepthTextureView() : null));
            params.uploadIfChanged();
        }
        // both textures are written for this frame: the same values as one uniform block (Tiles binds it on Apple's path)
        if (params != null) {
            if (block == null) block = new LensBlock();
            calmLast = block.write(framed ? lensFrame : null, params);
            boolean calm = calmLast && !noCalm;
            leaveCinematic = calm ? CINEMATIC_FULL : CINEMATIC_CALM;
            leaveUltra = calm ? ULTRA_FULL : ULTRA_CALM;
            if (framed && lensFrame != null && WorldProjection.ready()) {
                // the world's own matrices and sun over the CPU's estimate (LensFrame's texels 0-11 and 21, whole: a
                // value of the frame's own must never ride in one of them)
                var encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.copyTextureToBuffer(WorldProjection.output(), block.buffer, 0L, LensClient::nothing, 0, 0, 0, 12, 1);
                encoder.copyTextureToBuffer(WorldProjection.output(), block.buffer, LensFrame.SUN * 16L, LensClient::nothing, 0, WorldProjection.SUN, 0, 1, 1);
            }
            dev.awaken.turbo.tiles.Tiles.lensBlock(block.buffer);
        }
    }

    private static @org.jspecify.annotations.Nullable LensBlock block;

    private static void nothing() {}

    // the governor (SPEC §22 "Steady Light", the trim): when a frame came late, Lens's own tick work stands back for a
    // second (a third of its usual time a tick), so what little the CPU has to spare goes to the game's own spikes
    private static long lastFrameNanos, lateNanos;

    /** What a tick of the heightfield's or the block map's reading may take now, in nanoseconds. */
    static long tickBudget() {
        return System.nanoTime() - lateNanos < 1_000_000_000L ? 150_000L : 500_000L;
    }

    private static void writeFrame(RenderLevelStageEvent.AfterLevel e) {
        long nowNanos = System.nanoTime();
        // (19 ms: past the 16.7 of a frame at 60 with room for the clock's own wobble; a faster screen's frames never
        // reach it unless they are late too)
        if (lastFrameNanos != 0L && nowNanos - lastFrameNanos > 19_000_000L && nowNanos - lastFrameNanos < 250_000_000L) lateNanos = nowNanos;
        lastFrameNanos = nowNanos;
        var state = e.getLevelRenderState();
        float lx = moonLight ? -sunDir.x : sunDir.x, ly = moonLight ? -sunDir.y : sunDir.y, lz = moonLight ? -sunDir.z : sunDir.z;
        // the clouds' cover: the look's, thickening toward overcast in rain and storms (not for the rain a Rolling Storm
        // brings: its own cell is the cloud over you)
        float weather = Math.max(0f, rain - dev.awaken.living.client.StormSky.localRain());
        float cover = cloudsHere ? Math.min(0.95f, current[Look.CLOUDS] + weather * 0.4f) : 0f;
        long ticks = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        // (the drift by the frame's own share of the tick: stepped twenty times a second, clouds drawn exactly showed it)
        double drift = ((ticks % 768_000L) + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) * 0.04;
        lensFrame.clouds(cloudsHere ? cloudHeight : 0f, cover, drift, cloudColour);
        lensFrame.air(sunDir.x, sunDir.y, sunDir.z, air && sunHere ? 1f : 0f, skyR, skyG, skyB, starBrightness);
        // a land with a sky of its own (every world but the overworld): the Air draws the land's colours, its horizon
        // the game's fog colour with the look's haze tint over it (SPEC §22 "The Air in other worlds")
        float tintMix = graded[Look.HAZE_TINT_MIX];
        lensFrame.land(fogR + (graded[Look.HAZE_TINT] - fogR) * tintMix, fogG + (graded[Look.HAZE_TINT + 1] - fogG) * tintMix,
                fogB + (graded[Look.HAZE_TINT + 2] - fogB) * tintMix, ownSky() ? 1f : 0f);
        lensFrame.blocks(sunPass && sunHere && blocks != null ? LensBlocks.TRUSTED : 0f, blocks != null ? blocks.minY() : 0f);
        lensFrame.lit(litAir != null ? litAir.originX() : 0, litAir != null ? litAir.originY() : 0, litAir != null ? litAir.originZ() : 0,
                rays && litAir.valid());
        lensFrame.paths(showPaths);
        float[] small = forcedSmall != null ? forcedSmall : SMALL;
        lensFrame.small(small[0], small[1], small[2], small[3]);
        lensFrame.write(state.cameraRenderState, state.playerRenderState, zeroToOne, lx, ly, lz, lightStrength, moonLight, rain,
                sunHere ? heightfield : null, temporal);
        if (WorldProjection.ready()) {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(WorldProjection.output(), lensFrame.texture.getTexture(), 0, 0, 0, 0, 0, 12, 1);
            encoder.copyTextureToTexture(WorldProjection.output(), lensFrame.texture.getTexture(), 0, LensFrame.SUN, 0, WorldProjection.SUN, 0, 1, 1);
        }
    }

    private static final float[] AIR_FOG = new float[3];
    // the game's fog colour this frame, as its terrain was fogged with it
    private static float fogR, fogG, fogB;

    /** Whether this world's sky is its own under the Air: every world but the overworld (its biomes say its colours). */
    private static boolean ownSky() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() != Level.OVERWORLD;
    }

    /**
     * The game's fog under the Air (SPEC §22): while Lens draws the sky, the game's own fog takes the Air's horizon colour
     * toward where the camera looks. The game still fogs land whole by itself (the last tenth of the render distance, a
     * chunk fading in, the edge hold's mist) in the biome's fog colour, which its own sky shares at the horizon. The
     * Air's sky does not, so where a biome has a fog colour of its own (the Glass Steppe's is sand) that land stood as
     * flat sand shapes on a blue sky (Euan, playing, 11 Nov: "it's like brown fog but low quality").
     */
    private static void fogColour(ViewportEvent.ComputeFogColor e) {
        if (!active || !framed || !air || !sunHere) return;
        Camera camera = e.getCamera();
        // water, lava and powder snow keep their own fog; so do blindness and darkness, which black it out
        if (camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        if (camera.entity() instanceof net.minecraft.world.entity.LivingEntity living
                && (living.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || living.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS))) return;
        Minecraft mc = Minecraft.getInstance();
        // (the sun as the last frame had it: this is asked before the frame's own values are worked out)
        if (mc.level == null || sunDir.lengthSquared() < 0.5f) return;
        // a land with a sky of its own keeps its fog: there the Air's horizon is that colour, not the other way round
        if (ownSky()) return;
        var forward = camera.forwardVector();
        float hx = forward.x(), hz = forward.z(), len = (float) Math.sqrt(hx * hx + hz * hz);
        if (len < 1.0e-3f) {
            // straight up or down: the way the camera is turned
            float yaw = camera.yRot() * Mth.DEG_TO_RAD;
            hx = -Mth.sin(yaw);
            hz = Mth.cos(yaw);
            len = 1f;
        }
        dev.awaken.lens.LensAir.horizon(hx / len, hz / len, sunDir.x, sunDir.y, sunDir.z, rain, AIR_FOG);
        // the game's own darkening toward the void, kept (FogRenderer.computeFogColor)
        float range = mc.level.getLevelData().voidDarknessOnsetRange();
        float dark = Mth.clamp((range + mc.level.getMinY() - (float) camera.position().y) / range, 0f, 1f);
        float keep = (1f - dark) * (1f - dark);
        e.setRed(AIR_FOG[0] * keep);
        e.setGreen(AIR_FOG[1] * keep);
        e.setBlue(AIR_FOG[2] * keep);
    }

    private static void ensureParams() {
        if (params != null) return;
        params = new LensParams();
        Minecraft.getInstance().getTextureManager().register(PARAMS, params.texture);
        hand = new LensHand();
        Minecraft.getInstance().getTextureManager().register(HAND, hand);
        glow = LensGlow.make();
        Minecraft.getInstance().getTextureManager().register(GLOW, glow);
        Minecraft.getInstance().getTextureManager().register(FAR_DEPTH, dev.awaken.compat.client.FarTerrain.depthTexture());
        zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Awaken.LOG.info("[lens] ready ({} depth)", zeroToOne ? "zero-to-one" : "minus-one-to-one");
    }

    /** Once a frame, after the fog is set up and before the world and its post effects are drawn. */
    private static void frame(ViewportEvent.RenderFog e) {
        if (!active || params == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (framed) WorldProjection.want();
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
            if (lensFrame != null) lensFrame.sun(u, v, clip.w > 1.0e-4f);
            WorldProjection.sun(sunDir);
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
        // solid blocks over the camera's column (a cave, a room): Swift keeps the game's own darks there (SPEC §22)
        var eye = camera.position();
        int ex = Mth.floor(eye.x), ez = Mth.floor(eye.z);
        int roof = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex, ez);
        // and a roof the heightmap skips (Carillon's clay tiles, any mod block outside #minecraft:blocks_motion)
        if (roof <= eye.y + 0.5 && mc.level.getChunkSource().getChunk(ex >> 4, ez >> 4, ChunkStatus.FULL, false) instanceof LevelChunk ch) {
            if (camHas.length < ch.getSectionsCount()) camHas = new boolean[ch.getSectionsCount()];
            LensHeights.sections(ch, camHas);
            LensHeights.column(ch, camHas, ex & 15, ez & 15, camCol);
            roof = camCol[1];
        }
        float underTarget = roof > eye.y + 0.5 ? 1f : 0f;
        under += (underTarget - under) * (1f - (float) Math.exp(-dt * 6.0));
        fogR = fog.color.x();
        fogG = fog.color.y();
        fogB = fog.color.z();
        params.write(debug, graded, far, zeroToOne, u, v, visible, sunset, sr, sg, sb, fog.color.x(), fog.color.y(), fog.color.z(),
                // a far renderer moves vanilla's chunk fog out; Lens's haze keeps vanilla's render distance
                FarTerrain.renderDistanceFogEnd(fog.renderDistanceEnd), fog.environmentalEnd, tear > 0 ? Math.min(1f, tear + tearStep * partial) : 0f, crush, bloomBoost,
                UiHooks.focus(), motion, motionX, motionY,
                FarTerrain.active(), FarTerrain.readable(), FarTerrain.reverseZ(), FarTerrain.zeroToOne(), FarTerrain.near(), FarTerrain.far(), under,
                LensGrades.shader(), framed && !lit);
        params.uploadIfChanged();
    }

    private static float smooth(float x) {
        float t = Math.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
