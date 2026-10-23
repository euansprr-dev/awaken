package dev.awaken.descent.client;

import dev.awaken.descent.net.DescentPayloads;
import net.minecraft.network.chat.Component;

import java.util.List;

/** What the client knows about the player's Descent (from the server). */
public final class ClientDescent {
    private ClientDescent() {}

    public static DescentPayloads.Sync sync = new DescentPayloads.Sync(List.of(), List.of(), List.of(), 0, 1, false, -1);

    // Live HUD state
    public static int depth;
    public static Component status = Component.empty();
    public static int noise = -1;
    public static float noiseShown;
    public static int rules;

    static void onSync(DescentPayloads.Sync s) {
        sync = s;
    }

    static void onHud(DescentPayloads.Hud h) {
        depth = h.depth();
        status = h.status();
        noise = h.noise();
        rules = h.rules();
    }

    static void clear() {
        sync = new DescentPayloads.Sync(List.of(), List.of(), List.of(), 0, 1, false, -1);
        depth = 0;
        status = Component.empty();
        noise = -1;
        noiseShown = 0;
        rules = 0;
    }
}
