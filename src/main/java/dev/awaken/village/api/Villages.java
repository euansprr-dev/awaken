package dev.awaken.village.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * AWAKEN's settlements as other modules see them (SPEC §12, §28.6): where they are, how they know a player, what has
 * been done to them and what they remember. W4's troubles and growth build on these (the Burning reads the damage and
 * its blocks, the Rebuild clears it, the Journal reads the history). Server side; before the village module installs
 * itself every call answers "none".
 */
public final class Villages {
    private Villages() {}

    /** A settlement: its stable key, name, kind ("hamlet", "village"), land ("temperate", "frost", ...), origin, radius, its people's settlement id. */
    public record View(String key, String name, String kind, String family, BlockPos origin, int radius, int settlement, int people) {}

    /** Damage done to a settlement: where, the block that stood there, the play-day. */
    public record Damage(BlockPos pos, BlockState original, long day) {}

    /** The four standings, in order. */
    public static final int STRANGER = 0, KNOWN = 1, FRIEND = 2, KIN = 3;

    public interface Access {
        Optional<View> settlementAt(Level level, BlockPos pos);

        Optional<View> nearest(Level level, BlockPos pos, int within);

        List<View> all(Level level);

        int standing(ServerPlayer p, String key);

        void addStanding(ServerPlayer p, String key, int points, String why);

        List<Damage> damage(MinecraftServer server, String key);

        void repaired(MinecraftServer server, String key, BlockPos pos);

        List<String> history(MinecraftServer server, String key);

        void history(MinecraftServer server, String key, String line);

        int prosperity(MinecraftServer server, String key);

        void addProsperity(MinecraftServer server, String key, int delta);

        int mood(MinecraftServer server, String key);

        void addMood(MinecraftServer server, String key, int delta);

        boolean reserved(Level level, BlockPos pos, int margin);
    }

    private static @Nullable Access access;

    public static void install(Access a) {
        access = a;
    }

    /** The settlement a position lies in (within its radius), if any is running. */
    public static Optional<View> settlementAt(Level level, BlockPos pos) {
        return access == null ? Optional.empty() : access.settlementAt(level, pos);
    }

    /** The nearest settlement known to the world (met or planned) within a distance. */
    public static Optional<View> nearest(Level level, BlockPos pos, int within) {
        return access == null ? Optional.empty() : access.nearest(level, pos, within);
    }

    /** Every settlement running now. */
    public static List<View> all(Level level) {
        return access == null ? List.of() : access.all(level);
    }

    /** How a settlement knows a player: {@link #STRANGER} .. {@link #KIN}. */
    public static int standing(ServerPlayer p, String key) {
        return access == null ? STRANGER : access.standing(p, key);
    }

    /** Standing earned (positive) or lost at a running settlement. */
    public static void addStanding(ServerPlayer p, String key, int points, String why) {
        if (access != null) access.addStanding(p, key, points, why);
    }

    /** What has been broken there, with what stood there (oldest first). */
    public static List<Damage> damage(MinecraftServer server, String key) {
        return access == null ? List.of() : access.damage(server, key);
    }

    /** A block put back (the record of it goes). */
    public static void repaired(MinecraftServer server, String key, BlockPos pos) {
        if (access != null) access.repaired(server, key, pos);
    }

    /** Its short history, oldest first ("Day 4: First found.", "Day 9: Kestrel brought 12 iron ingots"). */
    public static List<String> history(MinecraftServer server, String key) {
        return access == null ? List.of() : access.history(server, key);
    }

    public static void history(MinecraftServer server, String key, String line) {
        if (access != null) access.history(server, key, line);
    }

    /** Prosperity, 0-100 (trade, filled requests and troubles solved raise it; W4 grows the village on it). */
    public static int prosperity(MinecraftServer server, String key) {
        return access == null ? 0 : access.prosperity(server, key);
    }

    public static void addProsperity(MinecraftServer server, String key, int delta) {
        if (access != null) access.addProsperity(server, key, delta);
    }

    /** Mood, 0-100 (damage and fear lower it). */
    public static int mood(MinecraftServer server, String key) {
        return access == null ? 0 : access.mood(server, key);
    }

    public static void addMood(MinecraftServer server, String key, int delta) {
        if (access != null) access.addMood(server, key, delta);
    }

    /**
     * Whether a position is a settlement's ground or within {@code margin} of it, planned settlements included (gates
     * never open there; a Hearth can't be founded there). No chunk loads.
     */
    public static boolean reserved(Level level, BlockPos pos, int margin) {
        return access != null && access.reserved(level, pos, margin);
    }
}
