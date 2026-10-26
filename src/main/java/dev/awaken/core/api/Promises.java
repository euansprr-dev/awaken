package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Promises (progression plan §5.4, SPEC §27): a lock the player met, kept for them. Whatever already says what it waits
 * for (a vault that stirs, a sealed door, a sky core, a sealed stratum, a ward at a climb site) also calls
 * {@link #seen}; the lock is then listed under "What waits" in the band (K) and pinned on the Atlas, and when the player
 * holds its key the world calls them back to it, once (the Horizon line raises it for a while, one toast). Nothing pops
 * when a lock is recorded, after the first time. {@link #kept} drops it once it's opened. Installed by the progression
 * module; until then every call does nothing.
 */
public final class Promises {
    private Promises() {}

    /** Need kinds. */
    public static final String RANK = "rank", LEVEL = "level", TIER = "tier";

    /**
     * What a lock waits for: a rank by its label ({@code rank:D}), a level ({@code level:20}) or a rung of the Ascent by
     * its tier name ({@code tier:tether}, {@code tier:wings}).
     */
    public record Need(String kind, String value) {
        public static Need rank(String label) { return new Need(RANK, label); }
        public static Need level(int level) { return new Need(LEVEL, Integer.toString(level)); }
        public static Need tier(String tier) { return new Need(TIER, tier); }

        /** In words, for a line of text: "D-rank", "level 20", "the Tether". */
        public String words() {
            return switch (kind) {
                case RANK -> value + "-rank";
                case LEVEL -> "level " + value;
                case TIER -> switch (value) {
                    case "tether" -> "the Tether";
                    case "leaper" -> "the Leaper";
                    case "wings" -> "Wings";
                    case "skyborn" -> "the open sky";
                    default -> value;
                };
                default -> value;
            };
        }
    }

    public interface Store {
        /**
         * Records a lock (once per key; nothing if the player already meets its need). {@code where} may be null for a
         * lock with no place (it's listed, never pinned or raised). {@code what} names it as the start of a sentence: "A
         * sealed vault", "Gardens, in the Descent".
         */
        void seen(ServerPlayer player, String key, @Nullable BlockPos where, Need need, String what);

        /** The lock was opened: drop it. */
        void kept(ServerPlayer player, String key);

        /** Drops every lock whose key starts with {@code prefix} (a rung given: every place that waited for it). */
        void keptAll(ServerPlayer player, String prefix);

        /** Re-reads every lock against what the player holds now and calls them back to any newly answered. */
        void recheck(ServerPlayer player);
    }

    private static Store store = new Store() {
        @Override public void seen(ServerPlayer player, String key, @Nullable BlockPos where, Need need, String what) {}
        @Override public void kept(ServerPlayer player, String key) {}
        @Override public void keptAll(ServerPlayer player, String prefix) {}
        @Override public void recheck(ServerPlayer player) {}
    };

    private static final List<BiPredicate<ServerPlayer, Need>> ANSWERS = new CopyOnWriteArrayList<>();

    public static void install(Store s) {
        store = s;
    }

    /** A module that grants a kind of key says when a player holds it (the Ascent answers {@link #TIER} needs). */
    public static void addAnswer(BiPredicate<ServerPlayer, Need> answer) {
        ANSWERS.add(answer);
    }

    /** Whether any installed answer says the player holds this need's key now. */
    public static boolean answered(ServerPlayer player, Need need) {
        for (var a : ANSWERS) if (a.test(player, need)) return true;
        return false;
    }

    public static void seen(ServerPlayer player, String key, @Nullable BlockPos where, Need need, String what) {
        store.seen(player, key, where, need, what);
    }

    public static void kept(ServerPlayer player, String key) {
        store.kept(player, key);
    }

    public static void keptAll(ServerPlayer player, String prefix) {
        store.keptAll(player, prefix);
    }

    public static void recheck(ServerPlayer player) {
        store.recheck(player);
    }
}
