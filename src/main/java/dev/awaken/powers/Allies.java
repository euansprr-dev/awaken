package dev.awaken.powers;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Entities temporarily on a player's side: decoys and afterimages (vanilla mannequins) and dominated mobs. In memory
 * only: every entry is short-lived and expires by game time.
 */
public final class Allies {
    private Allies() {}

    public enum Kind { DECOY, DOMINATED }

    public record Entry(UUID owner, Kind kind, long until) {}

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    public static void add(Entity e, UUID owner, Kind kind, long until) {
        ENTRIES.put(e.getUUID(), new Entry(owner, kind, until));
    }

    public static void remove(Entity e) {
        ENTRIES.remove(e.getUUID());
    }

    public static @Nullable Entry get(Entity e) {
        return ENTRIES.get(e.getUUID());
    }

    public static boolean isAlly(Entity e) {
        return !ENTRIES.isEmpty() && ENTRIES.containsKey(e.getUUID());
    }

    public static boolean isDominated(Entity e) {
        Entry en = get(e);
        return en != null && en.kind() == Kind.DOMINATED;
    }

    public static @Nullable UUID ownerOf(Entity e) {
        Entry en = get(e);
        return en == null ? null : en.owner();
    }

    static void expire(long now) {
        if (ENTRIES.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().until() < now) it.remove();
        }
    }

    static void clear() {
        ENTRIES.clear();
    }
}
