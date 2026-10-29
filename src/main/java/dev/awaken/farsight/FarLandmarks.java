package dev.awaken.farsight;

import dev.awaken.gates.GateState;
import dev.awaken.gates.Gates;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Landmarks on the horizon (SPEC §20): the few things worth seeing from kilometres away, drawn by Far Sight as tall
 * glowing columns that far terrain can hide. A module adds a {@link Source} once at init; sources are asked on the
 * server tick (every {@link #REFRESH_TICKS}) for what stands within Far Sight's reach of each player, and the client
 * draws the result. Gate pillars come from {@link Gates#near} (beyond the 1.5 km the gates draw their own pillars);
 * the city adds its towers the same way.
 */
public final class FarLandmarks {
    private FarLandmarks() {}

    static final int REFRESH_TICKS = 40, MAX = 48;

    /**
     * One landmark: a column {@code height} blocks tall rising from {@code pos}, {@code argb} its glow, drawn only from
     * {@code from} blocks away (nearer, its owner draws it as it really looks).
     */
    public record Landmark(BlockPos pos, int height, int argb, int from) {}

    @FunctionalInterface
    public interface Source {
        void collect(ServerLevel level, BlockPos around, int radius, Consumer<Landmark> out);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static volatile Map<ResourceKey<Level>, List<Landmark>> current = Map.of();
    private static int clock;

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** What to draw in a dimension now (any thread). */
    public static List<Landmark> current(ResourceKey<Level> dimension) {
        return current.getOrDefault(dimension, List.of());
    }

    /** Server tick, while far terrain is collected: asks every source around every player. */
    static void tick(MinecraftServer server) {
        if (++clock < REFRESH_TICKS) return;
        clock = 0;
        Map<ResourceKey<Level>, List<Landmark>> next = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level) || !FarCollector.wanted(level)) continue;
            List<Landmark> list = next.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
            for (Source s : SOURCES) {
                s.collect(level, p.blockPosition(), Rings.reach(Rings.RINGS - 1), l -> {
                    if (list.size() < MAX && !list.contains(l)) list.add(l);
                });
            }
        }
        current = next;
    }

    static void clear() {
        current = Map.of();
        clock = 0;
    }

    /** The gates' pillars by rank (E to Sovereign), as the gate pillars colour them; a broken gate burns red. */
    private static final int[] RANK = {0xFFF4F6FB, 0xFF5FD08F, 0xFF6FB3FF, 0xFFB48CFF, 0xFFF2A541, 0xFFFF5A6A, 0xFFD4AF37};

    static void gates(ServerLevel level, BlockPos around, int radius, Consumer<Landmark> out) {
        for (Gates.GateInfo g : Gates.near(level, around, radius)) {
            // as the gate pillars show them: not while it forms
            if (g.state() == GateState.CLEARED || g.state() == GateState.RELEASED || g.state() == GateState.BUILDING) continue;
            int argb = g.state() == GateState.BROKEN ? 0xFFFF2030 : RANK[Math.clamp(g.rank().ordinal(), 0, RANK.length - 1)];
            out.accept(new Landmark(g.pos(), 900, argb, 1500));
        }
    }
}
