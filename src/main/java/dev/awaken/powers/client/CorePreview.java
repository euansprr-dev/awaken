package dev.awaken.powers.client;

import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerPreviews;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.Powers;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.LinkedHashMap;
import java.util.List;

/** At most two read-only requests a second while a core is hovered; no background inventory scans. */
final class CorePreview {
    private CorePreview() {}
    private record Key(String power, int variant) {}
    private record Cached(PowerPreviews.Reply value, long at) {}
    private static final LinkedHashMap<Key, Cached> CACHE = new LinkedHashMap<>();
    private static long requestedAt;
    static List<String> lines(PowerType t, int variant) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return List.of("Connect to see your upgrade details.");
        long now = System.nanoTime();
        Key key = new Key(t.id.toString(), Math.max(0, Math.min(t.variants.size() - 1, variant)));
        if (requestedAt == 0 || now - requestedAt >= 500_000_000L) {
            requestedAt = now;
            ClientPacketDistributor.sendToServer(new PowerPreviews.Request(key.power(), key.variant()));
        }
        Cached cached = CACHE.get(key);
        Loadout l = Powers.clientLoadoutOrNull();
        var owned = l == null ? null : l.get(t.id.toString());
        if (cached == null || now - cached.at() > 1_500_000_000L || owned == null
                || cached.value().level() != owned.level() || cached.value().ownedVariant() != owned.variant()) {
            return List.of("Refreshing your exact upgrade details from the server…");
        }
        return cached.value().lines();
    }
    static void accept(PowerPreviews.Reply reply) {
        Key key = new Key(reply.power(), reply.coreVariant());
        CACHE.remove(key); CACHE.put(key, new Cached(reply, System.nanoTime()));
        while (CACHE.size() > 16) CACHE.remove(CACHE.keySet().iterator().next());
    }
    static void clear() { CACHE.clear(); requestedAt = 0; }
}
