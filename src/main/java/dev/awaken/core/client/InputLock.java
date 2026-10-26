package dev.awaken.core.client;

import dev.awaken.Awaken;
import dev.awaken.core.InputHolds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The one way to hold the player still on the client (SPEC §27.6): a scene camera, a hand-over, the Registry's wait, the
 * ritual. Owners hold and let go in any order; the real input is saved once and comes back when the last lets go
 * ({@link InputHolds}). Every hold has a deadline: past it the lock lets go and logs who forgot. A logout lets everything
 * go. Client only.
 */
public final class InputLock {
    private InputLock() {}

    private static final InputHolds<LocalPlayer, ClientInput> HOLDS = new InputHolds<>(p -> p.input, (p, i) -> p.input = i, ClientInput::new);
    private static long ticks;

    static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> HOLDS.clear());
    }

    /** {@code owner} holds the player still until {@link #release}, for at most {@code maxTicks} (the failsafe). */
    public static void hold(Object owner, int maxTicks) {
        HOLDS.hold(owner, Minecraft.getInstance().player, ticks, maxTicks);
    }

    public static void release(Object owner) {
        HOLDS.release(owner);
    }

    public static boolean held(Object owner) {
        return HOLDS.held(owner);
    }

    public static boolean held() {
        return HOLDS.held();
    }

    private static void tick() {
        ticks++;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            if (HOLDS.held()) HOLDS.clear();
            return;
        }
        for (Object late : HOLDS.tick(p, ticks)) Awaken.LOG.warn("[input] a hold outlived its time and was let go: {}", late);
    }
}
