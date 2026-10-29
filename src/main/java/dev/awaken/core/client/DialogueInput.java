package dev.awaken.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.api.Dialogue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Use advances spoken lines before vanilla interactions or scene handovers see the same press. */
final class DialogueInput {
    private DialogueInput() {}
    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, InputEvent.InteractionKeyMappingTriggered.class, e -> {
            Minecraft mc = Minecraft.getInstance();
            if (!e.isUseItem() || mc.player == null || mc.gui.screen() != null) return;
            if (Dialogue.use(e.getHand() == InteractionHand.MAIN_HAND)) {
                e.setCanceled(true);
                e.setSwingHand(false);
            } else if (e.getHand() == InteractionHand.MAIN_HAND && mc.crosshairPickEntity instanceof net.minecraft.world.entity.npc.Npc) {
                Dialogue.capture();
            }
        });
        // Real release events also catch a release and second press between two client ticks.
        bus.addListener(InputEvent.MouseButton.Pre.class, e -> {
            if (e.getAction() == InputConstants.RELEASE && Minecraft.getInstance().options.keyUse.matches(
                    InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) Dialogue.release();
        });
        bus.addListener(InputEvent.Key.class, e -> {
            if (e.getAction() == InputConstants.RELEASE && Minecraft.getInstance().options.keyUse.matches(e.getKeyEvent())) Dialogue.release();
        });
        bus.addListener(ClientTickEvent.Post.class, e -> {
            Minecraft mc = Minecraft.getInstance();
            Dialogue.tick();
            if (mc.player == null || mc.gui.screen() != null) Dialogue.reset();
            else if (!mc.options.keyUse.isDown()) Dialogue.release();
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> Dialogue.reset());
    }
}
