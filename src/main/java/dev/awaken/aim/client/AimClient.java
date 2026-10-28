package dev.awaken.aim.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.api.Aim;
import dev.awaken.core.api.HudState;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Lock-on, client half (SPEC §40.3-40.5): the lock key (middle mouse; pick block still works when nothing is marked), the
 * mark and the lock ({@link Lock}), their look ({@link Reticle}), the dot ({@link Dot}). The vanilla crosshair steps aside
 * on the clear screen. Installs {@code core.api.Aim}'s client side.
 */
public final class AimClient {
    private AimClient() {}

    /** Middle mouse (button 2, as vanilla's pick block). */
    public static final KeyMapping KEY = new KeyMapping("key.awaken.lock", KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE, 2, AwakenKeys.CATEGORY);

    public static void init(IEventBus modBus) {
        AimConfig.register(modBus);
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(KEY));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, Reticle.LAYER, Reticle::render));
        Aim.install(Lock::target, () -> Lock.locked >= 0);
        Aim.installBend(Lock::bend);
        Aim.installAnchor(Reticle::anchor);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Pre.class, e -> key());
        bus.addListener(ClientTickEvent.Post.class, e -> Lock.tick(Minecraft.getInstance()));
        bus.addListener(RenderFrameEvent.Pre.class, e -> Lock.frame(Minecraft.getInstance(), e.getPartialTick().getGameTimeDeltaPartialTick(false)));
        bus.addListener(CalculatePlayerTurnEvent.class, Lock::turn);
        bus.addListener(ExtractLevelRenderStateEvent.class, Reticle::extract);
        bus.addListener(InputEvent.MouseScrollingEvent.class, e -> {
            if (Lock.scroll(e.getScrollDeltaY())) e.setCanceled(true);
        });
        bus.addListener(RenderGuiLayerEvent.Pre.class, e -> {
            // the clear screen: the crosshair goes (the lock and the dot take its place)
            if (e.getName().equals(VanillaGuiLayers.CROSSHAIR) && !HudState.crosshair()) e.setCanceled(true);
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> Lock.reset(false));
    }

    /**
     * Before vanilla reads its keys: the lock key locks or lets go. Sharing a key with pick block (the default), a press
     * that locked or let go is the lock's alone; with nothing to lock it is pick block's, as it always was.
     */
    private static void key() {
        Minecraft mc = Minecraft.getInstance();
        boolean clicked = false;
        while (KEY.consumeClick()) clicked = true;
        if (!clicked || mc.player == null || mc.gui.screen() != null || !AimConfig.enabled() || Lock.paused(mc)) return;
        if (Lock.press(mc) && KEY.same(mc.options.keyPickItem)) {
            while (mc.options.keyPickItem.consumeClick()) {
                // spent on the lock
            }
        }
    }
}
