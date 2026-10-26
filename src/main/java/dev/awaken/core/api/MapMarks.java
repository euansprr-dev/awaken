package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Places other modules want on the Atlas (lairs you've heard of or found, locks you met, rumours). Read on the client
 * from synced data; registered once by each module's client entry. Gates have their own list ({@link #gates}): a
 * gate's pillar is seen from far off, so the Atlas stands it over the clouds as well as on land you know.
 */
public final class MapMarks {
    private MapMarks() {}

    /** One place: where, its colour, a short label, and whether the player has been there. */
    public record Mark(BlockPos pos, int colour, String label, boolean visited) {}

    public interface Source {
        List<Mark> marks(Player player);
    }

    /**
     * A gate you know of: its pillar of light (a live beacon) or a scanner's last reading. {@code rank} is the rank's
     * letter, {@code colour} its colour (the warning red when broken), {@code when} how long it holds ("breaks in 2
     * days", "broken"), {@code read} true for a scanner's reading rather than a pillar you can see.
     */
    public record Gate(BlockPos pos, String rank, int colour, String name, String when, boolean broken, boolean read) {}

    public interface GateSource {
        List<Gate> gates(Player player);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static final List<GateSource> GATES = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        SOURCES.add(source);
    }

    public static void registerGates(GateSource source) {
        GATES.add(source);
    }

    public static List<Mark> collect(Player player) {
        List<Mark> out = new ArrayList<>();
        for (Source s : SOURCES) out.addAll(s.marks(player));
        return out;
    }

    /** Every gate any module knows for this player; a scanner's reading of a gate whose pillar you see is dropped. */
    public static List<Gate> gates(Player player) {
        List<Gate> all = new ArrayList<>(), out = new ArrayList<>();
        for (GateSource s : GATES) all.addAll(s.gates(player));
        for (Gate g : all) if (!g.read()) out.add(g); // pillars you can see first
        for (Gate g : all) {
            if (!g.read()) continue;
            boolean seen = false;
            for (Gate o : out) seen |= Math.abs(o.pos().getX() - g.pos().getX()) + Math.abs(o.pos().getZ() - g.pos().getZ()) <= 24;
            if (!seen) out.add(g);
        }
        return out;
    }
}
