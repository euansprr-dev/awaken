package dev.awaken.compat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.compat.FrameStats;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * The Look switch's client wiring: its key (U, in Awaken's key category), the last minute of frame times for the
 * screen's frame-time line, and putting the chosen tier back into Distant Horizons' file when the game closes.
 */
public final class LookClient {
    private LookClient() {}

    public static final KeyMapping LOOK = new KeyMapping("key.awaken.look", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_O, AwakenKeys.CATEGORY);

    /** Frame-to-frame times: 32768 frames is a full minute up to ~540 fps. */
    static final FrameStats FRAMES = new FrameStats(32768);
    private static long lastFrame;

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(LOOK));
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> frame());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, e -> LookSwitch.onShutdown());
    }

    private static void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0) FRAMES.record(now - lastFrame);
        lastFrame = now;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (LOOK.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new LookScreen(null));
        }
    }
}
