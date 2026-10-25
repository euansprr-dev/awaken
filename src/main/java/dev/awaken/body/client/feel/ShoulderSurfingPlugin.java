package dev.awaken.body.client.feel;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.github.exopandora.shouldersurfing.api.client.event.handler.ComputeTargetCameraOffsetEventHandler;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import dev.awaken.Awaken;
import dev.awaken.body.client.ActionCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Shoulder Surfing Reloaded (a pack mod, optional, client only) as the action camera's view (SPEC §17.13). Shoulder
 * Surfing finds this through {@code shouldersurfing_plugin.json} and calls {@link #register}; after its own handlers
 * (sprint, aiming, flying: priorities -10000 to -7000) the action camera shapes the offset. The ONLY class that names
 * Shoulder Surfing: {@link ShoulderView} reaches its static methods only once {@code ModList} said the mod is loaded,
 * and any failure leaves Shoulder Surfing alone for the session.
 */
public final class ShoulderSurfingPlugin implements IShoulderSurfingPlugin {
    private static boolean broken;

    public ShoulderSurfingPlugin() {}

    @Override
    public void register(com.github.exopandora.shouldersurfing.api.event.IEventBus bus) {
        bus.register((ComputeTargetCameraOffsetEventHandler) e -> {
            if (broken || !(e.getCameraEntity() instanceof LocalPlayer)) return;
            try {
                e.setResult(ActionCamera.shoulder(e.getResult(), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
            } catch (RuntimeException ex) {
                broken = true;
                Awaken.LOG.warn("The action camera could not shape Shoulder Surfing's view; leaving it alone", ex);
            }
        });
        Awaken.LOG.info("[feel] the action camera drives Shoulder Surfing's view");
    }

    /** The View key with Shoulder Surfing: first person and the shoulder view, nothing else. */
    static boolean toggle() {
        if (broken) return false;
        try {
            IShoulderSurfing.getInstance().changePerspective(
                    Perspective.current() == Perspective.FIRST_PERSON ? Perspective.SHOULDER_SURFING : Perspective.FIRST_PERSON);
            return true;
        } catch (RuntimeException | LinkageError ex) {
            broken = true;
            Awaken.LOG.warn("Could not switch Shoulder Surfing's perspective; the View key falls back to vanilla's", ex);
            return false;
        }
    }

    /** Into the shoulder view if you're in first person. */
    static void shoulder() {
        if (broken) return;
        try {
            if (Perspective.current() == Perspective.FIRST_PERSON) IShoulderSurfing.getInstance().changePerspective(Perspective.SHOULDER_SURFING);
        } catch (RuntimeException | LinkageError ex) {
            broken = true;
            Awaken.LOG.warn("Could not switch to Shoulder Surfing's view", ex);
        }
    }
}
