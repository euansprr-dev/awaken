package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * The Horizon line (SPEC §17 "The Wilds"): the HUD's second line, just for exploration. The Now tracker says what you
 * could do next; the Horizon names one place worth crossing the world for: the nearest site you can reach now, and
 * once in a while a tease of one you can't reach yet ("needs Wings"). Gates, requests and unspent points never take it.
 * Modules register {@link Source}s of sites; movement systems register {@link Reach} answers for the needs sites name
 * (the Flight power and Jet Boots today, the Ascent's tiers later). Server side, every couple of seconds per player.
 */
public final class HorizonHooks {
    private HorizonHooks() {}

    /** The need of a site in the sky: some way up (the tease reads "needs Wings"). */
    public static final String WINGS = "wings";
    /** A tease shows for {@link #TEASE_FOR} ticks once every {@link #TEASE_EVERY} (15 s in 5 min), staggered per player. */
    public static final int TEASE_EVERY = 6000, TEASE_FOR = 300;

    /**
     * A site near the player. {@code id} is stable for the same place; {@code name} is what the line calls it ("Sealed
     * Vault", "Whispered: Monolith"); {@code where} is its centre; {@code need} is empty when anyone can walk there, else
     * the movement it waits for ({@link #WINGS}); {@code raised} marks a site the world pointed you at (a spirit's
     * whisper), shown before nearer ones.
     */
    public record Site(String id, String name, BlockPos where, String need, boolean raised) {}

    /** The line to draw: a site, and whether it is a tease (its need isn't met yet). */
    public record Line(String id, String name, BlockPos where, String need, boolean tease) {}

    /** Sites near one player, any order. Never invents a place; no sites is a fine answer. */
    @FunctionalInterface
    public interface Source {
        List<Site> sites(ServerPlayer player);
    }

    /** Whether a player has what a need asks for right now (a movement power, a frame module, a tier). */
    @FunctionalInterface
    public interface Reach {
        boolean can(ServerPlayer player, String need);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static final List<Reach> REACH = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        SOURCES.add(source);
    }

    public static void addReach(Reach reach) {
        REACH.add(reach);
    }

    /** Whether the player can reach a site with this need now: no need, or any installed answer says yes. */
    public static boolean can(ServerPlayer player, String need) {
        if (need.isEmpty()) return true;
        for (Reach r : REACH) if (r.can(player, need)) return true;
        return false;
    }

    /** Every site the sources know near this player (a failing source is skipped). */
    public static List<Site> sites(ServerPlayer player) {
        List<Site> all = new ArrayList<>();
        for (Source s : SOURCES) {
            try {
                all.addAll(s.sites(player));
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[horizon] a site source failed: {}", e.toString());
            }
        }
        return all;
    }

    /** The line for this player now. */
    public static Optional<Line> collect(ServerPlayer player) {
        List<Site> all = sites(player);
        if (all.isEmpty()) return Optional.empty();
        return pick(all, player.blockPosition(), need -> can(player, need), player.level().getGameTime(), player.getUUID().hashCode());
    }

    /**
     * Pure: the nearest site the player can reach (raised ones first), except during this player's tease window, when the
     * nearest one they can't reach takes the line. Nothing reachable and no tease window: no line.
     */
    public static Optional<Line> pick(List<Site> sites, BlockPos from, Predicate<String> can, long time, int stagger) {
        Site best = null, tease = null;
        double bestD = Double.MAX_VALUE, teaseD = Double.MAX_VALUE;
        for (Site s : sites) {
            double d = horizontal(from, s.where());
            if (s.need().isEmpty() || can.test(s.need())) {
                boolean better = best == null || (s.raised() != best.raised() ? s.raised() : d < bestD);
                if (better) {
                    best = s;
                    bestD = d;
                }
            } else if (d < teaseD) {
                tease = s;
                teaseD = d;
            }
        }
        if (tease != null && teasing(time, stagger)) return Optional.of(new Line(tease.id(), tease.name(), tease.where(), tease.need(), true));
        return best == null ? Optional.empty() : Optional.of(new Line(best.id(), best.name(), best.where(), best.need(), false));
    }

    /** Whether this player's tease window is open at {@code time}. */
    public static boolean teasing(long time, int stagger) {
        return Math.floorMod(time + stagger, (long) TEASE_EVERY) < TEASE_FOR;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
