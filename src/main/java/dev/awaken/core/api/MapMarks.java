package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Places other modules want on the Gate Map (lairs you've heard of or found). Read on the client from synced data;
 * registered once by each module's client entry.
 */
public final class MapMarks {
    private MapMarks() {}

    /** One place: where, its colour, a short label, and whether the player has been there. */
    public record Mark(BlockPos pos, int colour, String label, boolean visited) {}

    public interface Source {
        List<Mark> marks(Player player);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        SOURCES.add(source);
    }

    public static List<Mark> collect(Player player) {
        List<Mark> out = new ArrayList<>();
        for (Source s : SOURCES) out.addAll(s.marks(player));
        return out;
    }
}
