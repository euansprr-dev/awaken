package dev.awaken.lucidium.client;

import dev.awaken.lucidium.net.LucidiumNet;

import java.util.List;

/** What the client knows about the local player's dream (from the server's HUD payloads). */
public final class ClientDream {
    private ClientDream() {}

    static LucidiumNet.DreamHud hud = LucidiumNet.DreamHud.AWAKE;
    /** Smoothed lucidity for drawing. */
    static float shown;
    static int ticksLeft;
    /** Reality-check flicker (ticks left). */
    static int realityCheck;
    /** Ticks H has been held while lucid. */
    static int hold;
    static boolean manifestSent;
    static boolean dreamlessSent;
    static int lastSpotted;
    static int signFlash;
    static List<LucidiumNet.CodexLine> codex = List.of();

    public static boolean dreaming() {
        return hud.dreaming();
    }

    public static int shadowless() {
        return hud.dreaming() ? hud.shadowless() : -1;
    }

    static void update(LucidiumNet.DreamHud next) {
        if (!hud.dreaming() && next.dreaming()) shown = next.lucidity();
        if (next.spotted() != lastSpotted && next.dreaming()) signFlash = 40;
        lastSpotted = next.spotted();
        hud = next;
        ticksLeft = next.ticksLeft();
        if (!next.dreaming()) {
            realityCheck = 0;
            hold = 0;
        }
    }

    static void tick() {
        if (!hud.dreaming()) return;
        if (ticksLeft > 0) ticksLeft--;
        shown += (hud.lucidity() - shown) * 0.12f;
        // world sound: the dream's sound clears as you become lucid (muffled at 0, open by 80)
        float clarity = net.minecraft.util.Mth.clamp(shown / 80f, 0f, 1f);
        dev.awaken.core.audio.client.Soundscape.muffle(ClientDream.class, net.minecraft.util.Mth.lerp(clarity * clarity, 900f, 20000f), 1f, 10);
        if (realityCheck > 0) realityCheck--;
        if (signFlash > 0) signFlash--;
    }

    static void clear() {
        hud = LucidiumNet.DreamHud.AWAKE;
        shown = 0;
        realityCheck = 0;
        hold = 0;
        codex = List.of();
    }
}
