package dev.awaken.hearth;

import dev.awaken.signal.Signal;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Signal windows with a Y/N choice ("hearth:&lt;key&gt;"). The accept action re-validates everything when it runs, so an
 * old window can never pay twice or upgrade the wrong thing. One pending offer per key per player.
 */
public final class Offers {
    private Offers() {}

    public static final String PREFIX = "hearth";

    private record Pending(long expires, Consumer<ServerPlayer> onAccept) {}

    private static final Map<UUID, Map<String, Pending>> PENDING = new HashMap<>();

    static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> PENDING.clear());
        Signal.onAction(PREFIX, (player, action, accepted) -> {
            String key = action.substring(Math.min(action.length(), PREFIX.length() + 1));
            Map<String, Pending> mine = PENDING.get(player.getUUID());
            if (mine == null) return;
            Pending p = mine.remove(key);
            if (p == null || !accepted || player.level().getGameTime() > p.expires) return;
            p.onAccept.accept(player);
        });
    }

    /** Registers what Y does and returns the action id to put on the window. */
    public static String offer(ServerPlayer player, String key, int ttlTicks, Consumer<ServerPlayer> onAccept) {
        PENDING.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(key, new Pending(player.level().getGameTime() + ttlTicks + 40, onAccept));
        return PREFIX + ":" + key;
    }

    /** For tests and commands: accept a pending offer as if Y was pressed. */
    public static boolean accept(ServerPlayer player, String key) {
        Map<String, Pending> mine = PENDING.get(player.getUUID());
        Pending p = mine == null ? null : mine.remove(key);
        if (p == null) return false;
        p.onAccept.accept(player);
        return true;
    }

    static void forget(UUID player) {
        PENDING.remove(player);
    }
}
