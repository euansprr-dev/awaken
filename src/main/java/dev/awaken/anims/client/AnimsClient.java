package dev.awaken.anims.client;

import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Hands, the first-person system (SPEC §24, docs/anims/PLAN.md): installs itself as core.api.FirstPerson's player and
 * owns the hand pass (Hands) and the body drawn in the world in first person (WorldBody).
 */
public final class AnimsClient {
    private AnimsClient() {}

    public static void init(IEventBus modBus) {
        FirstPerson.install(Hands.INSTANCE);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> {
            Hands.tick();
            Touches.tick();
        });
        // touches (P4): the game's own interaction events, client side
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, Touches::rightClickBlock);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.class, Touches::leftClickBlock);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, Touches::entityInteract);
        bus.addListener(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered.class, Touches::interactionKey);
        bus.addListener(net.neoforged.neoforge.client.event.ScreenEvent.Opening.class, Touches::screenOpening);
        FirstPerson.view(Touches::viewPose);
        // the hands meet what they touch (P4.1); the view leans in a touch while one is out
        FirstPerson.world("anims_touch", 200, Reaches::pose);
        bus.addListener(net.neoforged.neoforge.client.event.ComputeFovModifierEvent.class, e -> {
            float lean = Reaches.lean(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            if (lean > 0 && !FirstPerson.reducedMotion.getAsBoolean()) e.setNewFovModifier(e.getNewFovModifier() * (1 - 0.05F * lean));
        });
        // station scenes (P5): the crafting table worked by hand
        dev.awaken.anims.client.station.StationClient.init(bus);
        bus.addListener(ClientTickEvent.Post.class, e -> dev.awaken.anims.client.station.StationClient.tick());
        bus.addListener(RenderFrameEvent.Pre.class, e -> WorldBody.frame());
        bus.addListener(ExtractLevelRenderStateEvent.class, WorldBody::onExtract);
        bus.addListener(SubmitCustomGeometryEvent.class, WorldBody::onSubmit);
        bus.addListener(EventPriority.HIGHEST, RenderHandEvent.class, Hands::first);
        bus.addListener(EventPriority.LOWEST, true, RenderHandEvent.class, Hands::lastOff);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            Hands.reset();
            WorldBody.reset();
            Touches.reset();
        });
    }
}
