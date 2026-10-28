package dev.awaken.core.client.dev;

import dev.awaken.Awaken;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Development only: drives the client from a script so builds can be checked visually without a person at the keys.
 * Inert unless the JVM is started with {@code -Dawaken.autopilot=<script>} (the {@code runAutopilot} Gradle task, which
 * uses its own game folder, never the player's saves). Sound is muted and the game keeps running in the background.
 *
 * <pre>
 * world &lt;name&gt; [creative|survival] [seed]   open the world, creating it if needed
 * waitworld                                      until the player stands in a loaded world
 * wait &lt;ticks&gt;
 * cmd &lt;command without slash&gt;
 * look &lt;yaw&gt; &lt;pitch&gt;
 * turn &lt;degrees per tick&gt; &lt;ticks&gt;             a steady turn that keeps going while later lines run (a sharp turn)
 * glide &lt;dx&gt; &lt;dy&gt; &lt;dz&gt; &lt;ticks&gt;               a steady flight (blocks a tick, spectator) that keeps going while later
 *                                                lines run: smooth, unlike a teleport a line (a flight's frames)
 * key &lt;awaken key name&gt;                        press one of the mod's keys (status, accept, dismiss, ...)
 * screen status|map|codex|holo|uiprobe|look|settings|close|experimental  holo: the holo K menu (status/map/codex stay flat);
 *                                                look: the Look switch (AWAKEN · LOOK); settings: Awaken's client
 *                                                settings section (Mods → AWAKEN → Config), with its Look row;
 *                                                uiprobe: every Ui/Glass primitive on black, for pixel checks;
 *                                                experimental: vanilla's experimental-settings prompt (it should answer itself)
 * click &lt;x&gt; &lt;y&gt;                                 a left click on the open screen at GUI coordinates
 * holo &lt;args&gt;                                    the holo module's test lines (HoloClient.autopilot): demo, at, run, hud, log
 * arms &lt;args&gt;                                    Arms' lines (ArmsAutopilot): strike, heavy, guard, dodge, pose, state
 * compat &lt;args&gt;                                  the companion plugins' lines (CompatClient.autopilot): inventory, jei ..., press, discovered, map
 * gui &lt;scale&gt;                                    GUI scale (0 = auto)
 * camera first|back|front                        the F5 views
 * fps &lt;label&gt;                                     logs the frames of the last second
 * perf &lt;label&gt; &lt;seconds&gt;                         records every frame's time, then logs p50/p95/p99 and the server tick
 * perfstart &lt;label&gt; ... perfend                 the same around the lines in between (a flight, a fight)
 * info                                           logs the renderer, device, framebuffer, heap, GC and loaded mods
 * window &lt;width&gt; &lt;height&gt;                       windowed at that size in points (Retina doubles the pixels)
 * opt renderDistance|simulationDistance &lt;n&gt;     changes a video option for the following scenes
 * lens off|swift|cinematic|ultra                switches Awaken's light layer, for same-moment A/B shots
 * lens chain <probe>|off                       measurement: awaken:lens_probe_<probe> (tools/lens/probes.py) in place of the Look's chain
 * lens temporal on|off|setting                  Lens 2's clouds and light spread over frames, or every pixel every frame
 * lookpreset performance|close_up|balanced|vista  the Look page's presets (Look, Far Sight, render distance, 4K scale, steady)
 * lookswitch swift|cinematic|ultra              picks a Look tier as a click on its card would (the open Look screen's,
 *                                                else straight through the switch)
 * pixels &lt;scale&gt; [fsr|sharp|bilinear]|config|state  Pixels' render scale and upscaler for the following scenes (not saved)
 * pixels gpu on|off|reset|&lt;label&gt;              GPU time of the world pass and the upscale (timestamps), logged per label
 * lensdebug 0..8                                 Lens debug views: normal, pass-through, depth masks, far depth, haze;
 *                                                Cinematic and Ultra: 5 light buffer, 6 normals, 7 the light alone,
 *                                                8 the classic Cinematic (before Lens 2) on the same frame
 * mdi on|off                                     vanilla terrain's multi-draw-indirect (the F3 debug toggle; on by
 *                                                default): off, GitHub's macOS runners (a paravirtual GPU) misplace sections
 * fardepth share|auto                           Distant Horizons shares its depth with Lens on every graphics API (to
 *                                                check it on Vulkan), or again only on the one it was checked on
 * shot &lt;name&gt;                                     screenshots/&lt;name&gt;.png in the game folder; with
 *                                                -Dawaken.autopilot.settle=&lt;s&gt; (CI tours) it first waits, at most that
 *                                                long, until the terrain around is loaded and built
 * shotnow &lt;name&gt;                                  the same this tick (no settle; for short moments)
 * leave                                          save and quit to the title (a following "world" line rejoins)
 * quit                                           save and quit to the title, then close the game
 * </pre>
 */
public final class Autopilot {
    private Autopilot() {}

    private static final List<String> LINES = new ArrayList<>();
    private static int index, waitTicks, waitWorldTicks, stuckTicks;
    private static boolean started;
    private static String createName;
    private static WorldCreationUiState.SelectedGameMode createMode;
    private static String createSeed;
    private static String shotPending;
    private static float turnStep;
    private static int turnTicks;
    private static double glideX, glideY, glideZ;
    private static int glideTicks;
    private static int walkTicks;
    // perf: frame-to-frame times of the scene being measured, preallocated so measuring costs no allocation per frame
    private static final long[] FRAMES = new long[1 << 15];
    private static int frameCount;
    private static long lastFrameNanos;
    private static String perfLabel;
    private static boolean perfTimed;
    // settle before shots: a slower machine (GitHub's 3-core runners) is still loading and building chunks after the
    // waits a script was written with on the Mac
    private static final int SETTLE_TICKS = Integer.getInteger("awaken.autopilot.settle", 0) * 20;
    private static int settleWaited, settleBuilt, settleQuiet, settleChunks, settleSpent;

    public static void init() {
        String path = System.getProperty("awaken.autopilot");
        if (path == null || path.isBlank()) return;
        try {
            LINES.addAll(Files.readAllLines(Path.of(path)));
        } catch (IOException e) {
            Awaken.LOG.warn("[autopilot] cannot read {}: {}", path, e.toString());
            return;
        }
        NeoForge.EVENT_BUS.addListener(Autopilot::tick);
        NeoForge.EVENT_BUS.addListener(Autopilot::frame);
        keepAwake();
        Awaken.LOG.info("[autopilot] {} lines from {}", LINES.size(), path);
    }

    // macOS naps a background app whose window is covered (App Nap and occlusion throttling: ~10 fps), which would
    // turn frame-time measurements into measurements of the window's visibility. A user-initiated activity keeps this
    // one process awake while the autopilot runs; it still lets the Mac sleep, and ends when the game closes.
    private static Object activity;

    private static void keepAwake() {
        if (!com.mojang.blaze3d.platform.MacosUtil.IS_MACOS) return;
        try {
            long userInitiatedAllowingSleep = 0x00EFFFFFL, latencyCritical = 0xFF00000000L;
            activity = ca.weblite.objc.Client.getInstance().sendProxy("NSProcessInfo", "processInfo")
                    .sendProxy("beginActivityWithOptions:reason:", userInitiatedAllowingSleep | latencyCritical, "AWAKEN autopilot");
            Awaken.LOG.info("[autopilot] App Nap off for this run");
        } catch (RuntimeException | LinkageError e) {
            Awaken.LOG.warn("[autopilot] could not keep the process awake: {}", e.toString());
        }
    }

    /**
     * Tours run on the MacBook's own display, never on an external monitor (an external 4K display changes every frame
     * time and the owner works there): the window moves to the built-in display, or the primary one if none is named so.
     */
    private static void onBuiltInDisplay(Minecraft mc) {
        if (!com.mojang.blaze3d.platform.MacosUtil.IS_MACOS) return;
        try {
            long window = mc.getWindow().handle();
            int target = org.lwjgl.sdl.SDLVideo.SDL_GetPrimaryDisplay();
            java.nio.IntBuffer displays = org.lwjgl.sdl.SDLVideo.SDL_GetDisplays();
            if (displays != null) {
                for (int i = 0; i < displays.remaining(); i++) {
                    String name = org.lwjgl.sdl.SDLVideo.SDL_GetDisplayName(displays.get(i));
                    if (name != null && name.startsWith("Built-in")) target = displays.get(i);
                }
                org.lwjgl.sdl.SDLStdinc.SDL_free(displays);
            }
            if (target != 0 && org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow(window) != target) {
                int centred = org.lwjgl.sdl.SDLVideo.SDL_WINDOWPOS_CENTERED_DISPLAY(target);
                org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition(window, centred, centred);
                Awaken.LOG.info("[autopilot] window moved to {}", org.lwjgl.sdl.SDLVideo.SDL_GetDisplayName(target));
            }
        } catch (RuntimeException | LinkageError e) {
            Awaken.LOG.warn("[autopilot] could not choose the display: {}", e.toString());
        }
    }

    private static boolean occluded(Minecraft mc) {
        return (org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(mc.getWindow().handle()) & org.lwjgl.sdl.SDLVideo.SDL_WINDOW_OCCLUDED) != 0;
    }

    private static void frame(RenderFrameEvent.Post e) {
        if (perfLabel == null) return;
        long now = System.nanoTime();
        if (lastFrameNanos != 0 && frameCount < FRAMES.length) FRAMES[frameCount++] = now - lastFrameNanos;
        lastFrameNanos = now;
    }

    /** Logs one scene's frame-time percentiles and the integrated server's tick time over its last 100 ticks. */
    private static void finishPerf(Minecraft mc) {
        String label = perfLabel;
        perfLabel = null;
        if (frameCount < 2) {
            Awaken.LOG.warn("[autopilot] perf {} recorded no frames", label);
            return;
        }
        long[] f = Arrays.copyOf(FRAMES, frameCount);
        Arrays.sort(f);
        long sum = 0;
        for (long v : f) sum += v;
        String tick = "n/a";
        var server = mc.getSingleplayerServer();
        if (server != null) {
            long[] t = server.getTickTimesNanos().clone();
            Arrays.sort(t);
            tick = String.format(java.util.Locale.ROOT, "%.2f", t[(int) (t.length * 0.95)] / 1e6);
        }
        Runtime rt = Runtime.getRuntime();
        Awaken.LOG.info(String.format(java.util.Locale.ROOT,
                "[autopilot] perf %s frames=%d avg_fps=%.1f p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms tick_p95=%sms heap=%d/%dMB fb=%dx%d load=%.1f occluded=%s minimized=%s",
                label, f.length, f.length / (sum / 1e9), pct(f, 0.50), pct(f, 0.95), pct(f, 0.99), f[f.length - 1] / 1e6, tick,
                (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20, mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                // the machine's 1-minute load: other builds or games running at the same time show up here
                ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(), occluded(mc),
                // a minimized window is held to 10 fps by the game itself (FramerateLimitTracker)
                mc.getWindow().isIconified()));
    }

    private static void startPerf(String label, boolean timed) {
        perfLabel = label;
        perfTimed = timed;
        frameCount = 0;
        lastFrameNanos = 0;
    }

    /**
     * With settling on: true at once if every chunk within the render distance has arrived (or none has come for 10 s: a
     * void cell, the world's edge) and every section the renderer knows is built (Sodium answers this too); else once
     * that has held for a second, past the sections' fade-in; or after SETTLE_TICKS. At most ten minutes a tour, so a world
     * that never settles costs a long tour no more than that. Called once a tick until true.
     */
    private static boolean settled(Minecraft mc, String shot) {
        if (SETTLE_TICKS <= 0 || mc.level == null || mc.player == null || settleSpent >= 20 * 600) return true;
        settleSpent++;
        int chunks = mc.level.getChunkSource().getLoadedChunksCount();
        settleQuiet = chunks == settleChunks ? settleQuiet + 1 : 0;
        settleChunks = chunks;
        boolean now = (settleQuiet >= 200 || chunksArrived(mc)) && mc.levelRenderer.hasRenderedAllSections();
        if (now && settleWaited == 0) { // nothing loading: the shot keeps the script's own timing
            settleQuiet = 0;
            return true;
        }
        settleBuilt = now ? settleBuilt + 1 : 0;
        boolean ready = settleBuilt >= 20;
        if (!ready && ++settleWaited < SETTLE_TICKS) return false;
        if (settleWaited > 20) {
            Awaken.LOG.info("[autopilot] settle {}: {} s{}", shot, settleWaited / 20, ready ? "" : " (gave up)");
        }
        settleWaited = settleBuilt = settleQuiet = 0;
        return true;
    }

    /** Every chunk within the render distance (less one, the edge the server sends last) is on the client. */
    private static boolean chunksArrived(Minecraft mc) {
        int r = Math.max(1, mc.options.getEffectiveRenderDistance() - 1);
        int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r && !mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    private static double pct(long[] sorted, double q) {
        return sorted[Math.min(sorted.length - 1, (int) (sorted.length * q))] / 1e6;
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            started = true;
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            mc.options.pauseOnLostFocus = false;
            // measure what the machine can really do: no vsync, no cap, no throttling while the window sits behind others
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            onBuiltInDisplay(mc);
        }
        // the window lost focus (the owner is using the Mac): never photograph the pause menu
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen && mc.level != null) mc.gui.setScreen(null);
        // walk: forward held down for a while (proves the player can move, e.g. after a scene let go)
        if (walkTicks > 0) {
            mc.options.keyUp.setDown(--walkTicks > 0);
            if (walkTicks == 0 && mc.player != null) Awaken.LOG.info("[autopilot] walked to {}", mc.player.blockPosition().toShortString());
        }
        if (turnTicks > 0 && mc.player != null) {
            turnTicks--;
            mc.player.setYRot(mc.player.getYRot() + turnStep);
            mc.player.setYHeadRot(mc.player.getYRot());
        }
        if (glideTicks > 0 && mc.player != null) {
            glideTicks--;
            mc.player.setDeltaMovement(glideTicks == 0 ? 0 : glideX, glideTicks == 0 ? 0 : glideY, glideTicks == 0 ? 0 : glideZ);
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (perfLabel != null && perfTimed) finishPerf(mc);
        if (createName != null) {
            if (!(mc.gui.screen() instanceof CreateWorldScreen cws)) return; // still loading the screen
            WorldCreationUiState ui = cws.getUiState();
            ui.setName(createName);
            ui.setGameMode(createMode);
            ui.setAllowCommands(true);
            if (createSeed != null) ui.setSeed(createSeed);
            for (var child : cws.children()) {
                if (child instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("selectWorld.create")) {
                    b.onPress(new MouseButtonInfo(0, 0));
                    break;
                }
            }
            createName = null;
            waitWorldTicks = 20 * 600;
            return;
        }
        if (waitWorldTicks > 0) {
            if (mc.level != null && mc.player != null && mc.gui.screen() == null) {
                waitWorldTicks = 0;
                waitTicks = 60; // let chunks and the HUD settle
            } else if (--waitWorldTicks == 0) {
                Awaken.LOG.warn("[autopilot] world never became ready");
            }
            return;
        }
        while (index < LINES.size()) {
            String line = LINES.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                index++;
                continue;
            }
            if (!run(mc, line)) return; // not ready yet: try the same line next tick
            index++;
            if (waitTicks > 0 || createName != null || waitWorldTicks > 0) return;
        }
    }

    /** Runs one line; false means "not ready, retry next tick". */
    private static boolean run(Minecraft mc, String line) {
        String[] a = line.split("\\s+", 2);
        String arg = a.length > 1 ? a[1] : "";
        switch (a[0]) {
            case "world" -> {
                if (!(mc.gui.screen() instanceof TitleScreen)) {
                    // a startup screen nobody will click (mod warnings, first-run prompts): after 5 s, go to the title
                    if (mc.gui.overlay() == null && mc.gui.screen() != null && ++stuckTicks > 100) {
                        Awaken.LOG.info("[autopilot] leaving {} for the title screen", mc.gui.screen().getClass().getSimpleName());
                        mc.gui.setScreen(new TitleScreen());
                        stuckTicks = 0;
                    }
                    return false;
                }
                String[] w = arg.split("\\s+");
                if (mc.getLevelSource().levelExists(w[0])) {
                    mc.createWorldOpenFlows().openWorld(w[0], () -> mc.gui.setScreen(new TitleScreen()));
                    waitWorldTicks = 20 * 600;
                } else {
                    createName = w[0];
                    createMode = w.length > 1 && w[1].equals("creative") ? WorldCreationUiState.SelectedGameMode.CREATIVE
                            : WorldCreationUiState.SelectedGameMode.SURVIVAL;
                    createSeed = w.length > 2 ? w[2] : null;
                    CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
                }
            }
            case "waitworld" -> waitWorldTicks = 20 * 600;
            case "wait" -> waitTicks = Integer.parseInt(arg.trim());
            case "cmd" -> {
                if (mc.player == null) return false;
                mc.player.connection.sendCommand(arg.startsWith("/") ? arg.substring(1) : arg);
                waitTicks = 5;
            }
            case "look" -> {
                if (mc.player == null) return false;
                String[] r = arg.split("\\s+");
                mc.player.setYRot(Float.parseFloat(r[0]));
                mc.player.setYHeadRot(Float.parseFloat(r[0]));
                mc.player.setXRot(Float.parseFloat(r[1]));
            }
            case "glide" -> { // glide <dx> <dy> <dz> <ticks>
                String[] r = arg.trim().split("\\s+");
                glideX = Double.parseDouble(r[0]);
                glideY = Double.parseDouble(r[1]);
                glideZ = Double.parseDouble(r[2]);
                glideTicks = Integer.parseInt(r[3]);
            }
            case "turn" -> { // turn <degrees per tick> <ticks>
                String[] r = arg.trim().split("\\s+");
                turnStep = Float.parseFloat(r[0]);
                turnTicks = Integer.parseInt(r[1]);
            }
            case "click" -> { // click <x> <y>, GUI coordinates, left button
                if (mc.gui.screen() == null) return false;
                String[] r = arg.trim().split("\\s+");
                var ev = new net.minecraft.client.input.MouseButtonEvent(Double.parseDouble(r[0]), Double.parseDouble(r[1]), new MouseButtonInfo(1, 0));
                mc.gui.screen().mouseClicked(ev, false);
                mc.gui.screen().mouseReleased(ev);
                waitTicks = 2;
            }
            case "holo" -> Awaken.LOG.info("[autopilot] holo {}: {}", arg.trim(), dev.awaken.holo.client.HoloClient.autopilot(arg));
            case "farsight" -> Awaken.LOG.info("[autopilot] farsight {}: {}", arg.trim(), dev.awaken.farsight.client.FarSightClient.autopilot(arg));
            case "arms" -> Awaken.LOG.info("[autopilot] arms {}: {}", arg.trim(), dev.awaken.arms.client.ArmsAutopilot.run(arg));
            case "compat" -> {
                Awaken.LOG.info("[autopilot] compat {}: {}", arg.trim(), dev.awaken.compat.client.CompatClient.autopilot(arg));
                waitTicks = 10;
            }
            case "walk" -> { // walk <ticks>: hold forward that long, logging where you started and where you got to
                walkTicks = Integer.parseInt(arg.trim());
                if (mc.player != null) Awaken.LOG.info("[autopilot] walk from {}", mc.player.blockPosition().toShortString());
                mc.options.keyUp.setDown(true);
            }
            case "use" -> { // the use key once (right click): places the held block where the crosshair rests
                KeyMapping.click(mc.options.keyUse.getKey());
                waitTicks = 2;
            }
            case "key" -> {
                KeyMapping k = KEYS.get(arg.trim());
                if (k == null) Awaken.LOG.warn("[autopilot] unknown key {}", arg);
                else KeyMapping.click(k.getKey());
                waitTicks = 2;
            }
            case "screenkey" -> { // a key straight to the open screen: tab, t, m, c, space, q, e, or shift+ one of them
                String k = arg.trim();
                boolean shift = k.startsWith("shift+");
                int code = switch (shift ? k.substring(6) : k) {
                    case "tab" -> com.mojang.blaze3d.platform.InputConstants.KEY_TAB;
                    case "t" -> com.mojang.blaze3d.platform.InputConstants.KEY_T;
                    case "m" -> com.mojang.blaze3d.platform.InputConstants.KEY_M;
                    case "c" -> com.mojang.blaze3d.platform.InputConstants.KEY_C;
                    case "space" -> com.mojang.blaze3d.platform.InputConstants.KEY_SPACE;
                    case "q" -> com.mojang.blaze3d.platform.InputConstants.KEY_Q;
                    case "e" -> com.mojang.blaze3d.platform.InputConstants.KEY_E;
                    default -> -1;
                };
                if (code < 0) Awaken.LOG.warn("[autopilot] unknown screen key {}", k);
                else if (mc.gui.screen() != null)
                    mc.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(code, 0, shift ? com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT : 0));
                waitTicks = 2;
            }
            case "trails" -> dev.awaken.trails.client.TrailsClient.debug(arg); // trails <now|browse [area]|open>: the Journal's TRAILS section
            case "screen" -> {
                if (arg.trim().equals("close")) mc.gui.setScreen(null);
                else if (arg.trim().equals("status")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.STATUS));
                else if (arg.trim().equals("powers")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.POWERS));
                else if (arg.trim().equals("journal")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.JOURNAL));
                else if (arg.trim().equals("map")) openMapPage(mc); // the band's Map page: the map in the world (SPEC §30.5)
                else if (arg.trim().equals("table")) {
                    mc.gui.setScreen(null);
                    dev.awaken.atlas.client.AtlasClient.openTable();
                }
                else if (arg.trim().equals("codex")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.CODEX));
                else if (arg.trim().equals("uiprobe")) mc.gui.setScreen(new UiProbeScreen());
                else if (arg.trim().equals("look")) mc.gui.setScreen(new dev.awaken.compat.client.LookScreen(null));
                else if (arg.trim().equals("settings")) {
                    // Awaken's settings as Mods → AWAKEN → Config builds them, opened at the client section (its Look row)
                    var mod = ModList.get().getModContainerById(Awaken.MODID).orElseThrow();
                    Screen top = mod.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).orElseThrow().createScreen(mod, null);
                    var client = net.neoforged.fml.config.ModConfigs.getModConfigs(Awaken.MODID).stream()
                            .filter(c -> c.getFileName().equals(Awaken.MODID + "-client.toml")).findFirst().orElseThrow();
                    mc.gui.setScreen(top instanceof net.neoforged.neoforge.client.gui.ConfigurationScreen cs
                            ? dev.awaken.compat.client.LookSettings.section(cs, net.neoforged.fml.config.ModConfig.Type.CLIENT, client,
                                    net.minecraft.network.chat.Component.literal("AWAKEN · client settings"))
                            : top);
                }
                else if (arg.trim().startsWith("holo")) {
                    String t = arg.trim().length() > 4 ? arg.trim().substring(4).trim() : "status";
                    var tab = switch (t) {
                        case "powers" -> dev.awaken.progression.client.StatusSections.Tab.POWERS;
                        case "journal" -> dev.awaken.progression.client.StatusSections.Tab.JOURNAL;
                        case "map" -> dev.awaken.progression.client.StatusSections.Tab.MAP;
                        case "codex" -> dev.awaken.progression.client.StatusSections.Tab.CODEX;
                        case "truths" -> dev.awaken.progression.client.StatusSections.Tab.TRUTHS;
                        default -> dev.awaken.progression.client.StatusSections.Tab.STATUS;
                    };
                    if (tab == dev.awaken.progression.client.StatusSections.Tab.MAP) openMapPage(mc);
                    else mc.gui.setScreen(new dev.awaken.holo.client.HoloStatusScreen(tab));
                }
                else if (arg.trim().equals("experimental")) {
                    // vanilla's own world-creation prompt, as a new world would show it: ExperimentalWarning should answer it
                    var back = mc.gui.screen();
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                        Awaken.LOG.info("[autopilot] experimental prompt answered: {}", yes ? "yes" : "no");
                        mc.gui.setScreen(back);
                    }, net.minecraft.network.chat.Component.translatable("selectWorld.warning.experimental.title"),
                            net.minecraft.network.chat.Component.translatable("selectWorld.warning.experimental.question")));
                }
                waitTicks = 10;
            }
            case "scroll" -> { // scroll <notches>: the open screen's wheel (negative scrolls down)
                if (mc.gui.screen() != null) mc.gui.screen().mouseScrolled(0, 0, 0, Double.parseDouble(arg.trim()));
                waitTicks = 2;
            }
            case "gui" -> {
                mc.options.guiScale().set(Integer.parseInt(arg.trim()));
                mc.resizeGui();
                waitTicks = 5;
            }
            case "hud" -> { // hud off|on: the F1 view, for clean shots
                if (mc.gui.hud.isHidden() != arg.trim().equals("off")) mc.gui.hud.toggle();
            }
            case "shot" -> {
                if (shotPending == null && !settled(mc, arg.trim())) return false;
                // command feedback is not part of the picture: clear the chat, let a few frames render without it
                if (shotPending == null) {
                    mc.gui.hud.getChat().clearMessages(false);
                    shotPending = arg.trim();
                    waitTicks = 3;
                    return false;
                }
                String name = shotPending;
                shotPending = null;
                Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                        msg -> Awaken.LOG.info("[autopilot] screenshot {}", name));
                waitTicks = 10;
            }
            case "shotnow" -> { // a shot this tick, without "shot"'s 3-tick settle (for moments shorter than that)
                String name = arg.trim();
                Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                        msg -> Awaken.LOG.info("[autopilot] screenshot {}", name));
                waitTicks = 1;
            }
            case "camera" -> { // first | back | front
                String v = arg.trim();
                mc.options.setCameraType(v.equals("back") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        : v.equals("front") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON);
                waitTicks = 2;
            }
            case "fps" -> Awaken.LOG.info("[autopilot] fps {} {}", arg.trim(), mc.getFps());
            case "perf" -> { // perf <label> <seconds>
                if (mc.level == null) return false;
                String[] p = arg.trim().split("\\s+");
                startPerf(p[0], true);
                waitTicks = 20 * (p.length > 1 ? Integer.parseInt(p[1]) : 10);
            }
            case "perfstart" -> {
                if (mc.level == null) return false;
                startPerf(arg.trim(), false);
            }
            case "perfend" -> {
                if (perfLabel != null) finishPerf(mc);
            }
            case "info" -> {
                var d = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo();
                StringBuilder gc = new StringBuilder();
                for (var b : ManagementFactory.getGarbageCollectorMXBeans()) gc.append(gc.isEmpty() ? "" : ",").append(b.getName());
                StringBuilder mods = new StringBuilder();
                for (var m : ModList.get().getMods()) mods.append(mods.isEmpty() ? "" : ",").append(m.getModId());
                Awaken.LOG.info("[autopilot] info backend={} driver={} device={} display={} fb={}x{} window={}x{} rd={} sd={} heapMax={}MB gc={} mods={}",
                        d.backendName(), d.driverInfo(), d.name(),
                        org.lwjgl.sdl.SDLVideo.SDL_GetDisplayName(org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow(mc.getWindow().handle())).replace(' ', '_'),
                        mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                        mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight(), mc.options.renderDistance().get(),
                        mc.options.simulationDistance().get(), Runtime.getRuntime().maxMemory() >> 20, gc, mods);
            }
            case "window" -> { // window <width> <height>, in points, on the Mac's own display
                String[] w = arg.trim().split("\\s+");
                mc.getWindow().setWindowed(Integer.parseInt(w[0]), Integer.parseInt(w[1]));
                onBuiltInDisplay(mc);
                waitTicks = 20;
            }
            case "lens" -> {
                if (arg.trim().startsWith("block")) {
                    Awaken.LOG.info("[autopilot] lens {}: {}", arg.trim(), dev.awaken.turbo.tiles.Tiles.block(arg.trim().substring(5)));
                    waitTicks = 20;
                    return true;
                }
                if (arg.trim().startsWith("tiles")) {
                    Awaken.LOG.info("[autopilot] lens {}: {}", arg.trim(), dev.awaken.turbo.tiles.Tiles.autopilot(arg.trim().substring(5)));
                    waitTicks = 20;
                    return true;
                }
                if (arg.trim().startsWith("chain")) {
                    Awaken.LOG.info("[autopilot] lens {}: {}", arg.trim(), dev.awaken.lens.client.LensClient.chain(arg.trim().substring(5)));
                    waitTicks = 20;
                    return true;
                }
                if (arg.trim().startsWith("temporal")) {
                    Awaken.LOG.info("[autopilot] lens {}: {}", arg.trim(), dev.awaken.lens.client.LensClient.temporal(arg.trim().substring(8)));
                    waitTicks = 20;
                    return true;
                }
                dev.awaken.core.AwakenConfig.Client.LENS.set(dev.awaken.core.AwakenConfig.Client.LensLook.valueOf(arg.trim().toUpperCase(java.util.Locale.ROOT)));
                waitTicks = 40; // Lens re-asserts itself on the next tick, then eases in
            }
            case "lookpreset" -> { // performance|close_up|balanced|vista: the Look page's preset, as a click would
                var p = dev.awaken.compat.client.LookPresets.Preset.valueOf(arg.trim().toUpperCase(java.util.Locale.ROOT));
                Awaken.LOG.info("[autopilot] look preset {}: {}", p, dev.awaken.compat.client.LookPresets.applyFromAutopilot(p) ? "applied" : "not available here");
                waitTicks = 40;
            }
            case "lookswitch" -> {
                var tier = dev.awaken.compat.Look.Tier.valueOf(arg.trim().toUpperCase(java.util.Locale.ROOT));
                boolean took = mc.gui.screen() instanceof dev.awaken.compat.client.LookScreen look ? look.pick(tier)
                        : dev.awaken.compat.client.LookSwitch.apply(tier);
                Awaken.LOG.info("[autopilot] look {}: {}", tier, took ? "applied" : "not available here");
                waitTicks = 40; // Lens re-asserts itself on the next tick, then eases in
            }
            case "pixels" -> { // pixels <scale> [fsr|sharp|bilinear] | config | state | gpu ...: Pixels for the following scenes
                Awaken.LOG.info("[autopilot] pixels {}: {}", arg.trim(), dev.awaken.turbo.pixels.Pixels.autopilot(arg));
                if (!arg.trim().startsWith("gpu")) waitTicks = 10;
            }
            case "lensdebug" -> {
                dev.awaken.lens.client.LensClient.debug(Integer.parseInt(arg.trim()));
                waitTicks = 10;
            }
            case "mdi" -> {
                mc.multiDrawIndirect = arg.trim().equals("on");
                waitTicks = 10;
            }
            case "fardepth" -> {
                dev.awaken.compat.client.CompatClient.shareFarDepthEverywhere("share".equals(arg.trim()));
                waitTicks = 10;
            }
            case "opt" -> { // opt renderDistance|simulationDistance|guiScale|gamma <n> (guiScale 0: auto; gamma in %, 100: Bright)
                String[] o = arg.trim().split("\\s+");
                int v = Integer.parseInt(o[1]);
                switch (o[0]) {
                    case "renderDistance" -> mc.options.renderDistance().set(v);
                    case "simulationDistance" -> mc.options.simulationDistance().set(v);
                    case "guiScale" -> {
                        mc.options.guiScale().set(v);
                        mc.resizeGui();
                    }
                    case "gamma" -> mc.options.gamma().set(v / 100.0);
                    default -> Awaken.LOG.warn("[autopilot] unknown option {}", o[0]);
                }
                waitTicks = 20;
            }
            case "leave" -> {
                if (mc.level != null) mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
            case "quit" -> {
                // the pause menu's "Save and Quit to Title" first, so the world and the player are saved as in play
                if (mc.level != null) {
                    mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                    return false;
                }
                mc.stop();
            }
            default -> Awaken.LOG.warn("[autopilot] unknown line: {}", line);
        }
        Awaken.LOG.info("[autopilot] {}", line);
        return true;
    }

    /** The band's Map page: whatever it opens (the Atlas table), as picking it on the rail would. */
    private static void openMapPage(Minecraft mc) {
        var action = dev.awaken.progression.client.StatusSections.action(dev.awaken.progression.client.StatusSections.Tab.MAP);
        mc.gui.setScreen(null);
        if (action != null) action.open().run();
        else Awaken.LOG.warn("[autopilot] the Map page opens nothing");
    }

    private static final Map<String, KeyMapping> KEYS = Map.of(
            "status", AwakenKeys.STATUS, "accept", AwakenKeys.ACCEPT, "dismiss", AwakenKeys.DISMISS,
            "power1", AwakenKeys.POWER_1, "power2", AwakenKeys.POWER_2, "movement", AwakenKeys.MOVEMENT,
            "ultimate", AwakenKeys.ULTIMATE, "focus", AwakenKeys.FOCUS, "bind", AwakenKeys.BIND, "rope", AwakenKeys.ROPE);
}
