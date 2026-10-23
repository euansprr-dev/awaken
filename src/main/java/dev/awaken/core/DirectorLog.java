package dev.awaken.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A short memory of the decisions the game made for each player (gate rolls, directive offers, path offers...),
 * printed by {@code /awaken why} so any choice can be explained from its real inputs. In memory only.
 */
public final class DirectorLog {
    private DirectorLog() {}

    public record Entry(long gameTime, String system, String detail) {}

    private static final int KEEP = 40;
    private static final Map<UUID, Deque<Entry>> LOG = new HashMap<>();

    public static synchronized void log(ServerPlayer player, String system, String detail) {
        Deque<Entry> d = LOG.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        d.addFirst(new Entry(player.level().getGameTime(), system, detail));
        while (d.size() > KEEP) d.removeLast();
    }

    public static synchronized void clear() {
        LOG.clear();
    }

    public static synchronized List<Entry> recent(UUID player, int n) {
        Deque<Entry> d = LOG.get(player);
        if (d == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (Entry e : d) {
            if (out.size() >= n) break;
            out.add(e);
        }
        return out;
    }
}
