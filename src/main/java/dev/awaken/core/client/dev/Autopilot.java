package dev.awaken.core.client.dev;

import dev.awaken.Awaken;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * key &lt;awaken key name&gt;                        press one of the mod's keys (status, accept, dismiss, ...)
 * screen status|close|experimental             experimental: vanilla's experimental-settings prompt (it should answer itself)
 * gui &lt;scale&gt;                                    GUI scale (0 = auto)
 * camera first|back|front                        the F5 views
 * fps &lt;label&gt;                                     logs the frames of the last second
 * shot &lt;name&gt;                                     screenshots/&lt;name&gt;.png in the game folder
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
        Awaken.LOG.info("[autopilot] {} lines from {}", LINES.size(), path);
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
        }
        // the window lost focus (the owner is using the Mac): never photograph the pause menu
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen && mc.level != null) mc.gui.setScreen(null);
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
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
            case "key" -> {
                KeyMapping k = KEYS.get(arg.trim());
                if (k == null) Awaken.LOG.warn("[autopilot] unknown key {}", arg);
                else KeyMapping.click(k.getKey());
                waitTicks = 2;
            }
            case "screen" -> {
                if (arg.trim().equals("close")) mc.gui.setScreen(null);
                else if (arg.trim().equals("status")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.STATUS));
                else if (arg.trim().equals("map")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.GATE_MAP));
                else if (arg.trim().equals("codex")) mc.gui.setScreen(new dev.awaken.progression.client.StatusScreen(dev.awaken.progression.client.StatusSections.Tab.CODEX));
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
            case "gui" -> {
                mc.options.guiScale().set(Integer.parseInt(arg.trim()));
                mc.resizeGui();
                waitTicks = 5;
            }
            case "shot" -> {
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
            case "camera" -> { // first | back | front
                String v = arg.trim();
                mc.options.setCameraType(v.equals("back") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        : v.equals("front") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON);
                waitTicks = 2;
            }
            case "fps" -> Awaken.LOG.info("[autopilot] fps {} {}", arg.trim(), mc.getFps());
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

    private static final Map<String, KeyMapping> KEYS = Map.of(
            "status", AwakenKeys.STATUS, "accept", AwakenKeys.ACCEPT, "dismiss", AwakenKeys.DISMISS,
            "power1", AwakenKeys.POWER_1, "power2", AwakenKeys.POWER_2, "movement", AwakenKeys.MOVEMENT,
            "ultimate", AwakenKeys.ULTIMATE, "focus", AwakenKeys.FOCUS, "bind", AwakenKeys.BIND);
}
