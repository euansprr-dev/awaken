package dev.awaken.hearth.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.hearth.net.HearthNet;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** B: tap to bind a lingering Echo (or call/dismiss your army), hold to open the Echo chooser. */
final class EchoKeys {
    private EchoKeys() {}

    static final int HOLD_TICKS = 8;
    private static int held;
    private static boolean opened;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean clicked = false;
        while (AwakenKeys.BIND.consumeClick()) clicked = true;
        if (mc.player == null || mc.gui.screen() != null) {
            if (!(mc.gui.screen() instanceof EchoScreen)) held = 0;
            return;
        }
        boolean down = AwakenKeys.BIND.isDown();
        if (down) {
            held++;
            if (held == HOLD_TICKS && !opened) {
                opened = true;
                mc.gui.setScreen(new EchoScreen());
            }
            return;
        }
        boolean tap = (held > 0 && held < HOLD_TICKS && !opened) || (held == 0 && clicked);
        held = 0;
        opened = false;
        if (tap) ClientPacketDistributor.sendToServer(new HearthNet.BindKey());
    }
}
